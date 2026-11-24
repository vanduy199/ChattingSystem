package com.ChattingSystem.media.repository;

import com.ChattingSystem.media.repository.dto.DeletedPurgeTarget;
import com.ChattingSystem.media.repository.dto.UploadingCleanupTarget;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

/**
 * DAO xử lý các thao tác dọn dẹp rác (GC) theo lô với PostgreSQL CTE FOR UPDATE SKIP LOCKED.
 * <p>
 * Toàn bộ tham số thời gian truyền vào câu lệnh SQL đều được chuyển đổi sang OffsetDateTime UTC
 * để tương thích chính xác với kiểu cột TIMESTAMPTZ của PostgreSQL, chống lệch múi giờ.
 */
@Repository
public class MediaAssetCleanupDao {

    /**
     * Khóa phân tán Postgres advisory lock (64-bit) dùng cho việc chạy dọn dẹp các quy tắc DB-only.
     * Khóa tự động được giải phóng khi kết thúc transaction (commit hoặc rollback).
     */
    public static final long DB_CLEANUP_LOCK_KEY = 738491029384756182L;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MediaAssetCleanupDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Thử lấy khóa phân tán non-blocking cho tiến trình dọn dẹp DB-only trong transaction hiện tại.
     * Trả về true nếu lấy được khóa, false nếu instance khác đang giữ khóa.
     */
    public boolean tryAcquireDbCleanupLock() {
        String sql = "SELECT pg_try_advisory_xact_lock(:lockKey)";
        MapSqlParameterSource params = new MapSqlParameterSource("lockKey", DB_CLEANUP_LOCK_KEY);
        Boolean acquired = jdbcTemplate.queryForObject(sql, params, Boolean.class);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * Quy tắc 1: Xử lý các bản ghi CREATING quá hạn (created_at < cutoff).
     * Chuyển trực tiếp sang FAILED với last_error = 'CREATE_TIMEOUT'.
     */
    public int cleanupCreatingTimeout(Instant cutoff, Instant now, int batchSize) {
        if (batchSize <= 0) {
            return 0;
        }

        String sql = """
            WITH candidate AS (
                SELECT id
                FROM media_assets
                WHERE status = 'CREATING'
                  AND created_at < :cutoff
                ORDER BY created_at ASC, id ASC
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            UPDATE media_assets a
            SET status = 'FAILED',
                last_error = 'CREATE_TIMEOUT',
                updated_at = :now
            FROM candidate c
            WHERE a.id = c.id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cutoff", toUtcOffset(cutoff))
                .addValue("now", toUtcOffset(now))
                .addValue("batchSize", batchSize);

        return jdbcTemplate.update(sql, params);
    }

    /**
     * Quy tắc 3: Xử lý các bản ghi COMPLETING quá hạn (updated_at < cutoff) do tiến trình chết giữa chừng.
     * Hoàn lại trạng thái về UPLOADING để người dùng có thể tiếp tục hoặc thử lại ghép mảnh.
     * LƯU Ý: Dùng updated_at, KHÔNG dùng created_at.
     */
    public int revertCompletingTimeout(Instant cutoff, Instant now, int batchSize) {
        if (batchSize <= 0) {
            return 0;
        }

        String sql = """
            WITH candidate AS (
                SELECT id
                FROM media_assets
                WHERE status = 'COMPLETING'
                  AND updated_at < :cutoff
                ORDER BY updated_at ASC, id ASC
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            UPDATE media_assets a
            SET status = 'UPLOADING',
                updated_at = :now
            FROM candidate c
            WHERE a.id = c.id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cutoff", toUtcOffset(cutoff))
                .addValue("now", toUtcOffset(now))
                .addValue("batchSize", batchSize);

        return jdbcTemplate.update(sql, params);
    }

    /**
     * Quy tắc 4: Xử lý các bản ghi FAILED hoặc BLOCKED quá hạn (updated_at < cutoff).
     * Chuyển sang DELETED để chuẩn bị xóa vật lý trên storage sau thời gian ân hạn.
     */
    public int cleanupFailedAndBlocked(Instant cutoff, Instant now, int batchSize) {
        if (batchSize <= 0) {
            return 0;
        }

        String sql = """
            WITH candidate AS (
                SELECT id
                FROM media_assets
                WHERE status IN ('FAILED', 'BLOCKED')
                  AND updated_at < :cutoff
                ORDER BY updated_at ASC, id ASC
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            UPDATE media_assets a
            SET status = 'DELETED',
                deleted_at = :now,
                locked_until = null,
                updated_at = :now
            FROM candidate c
            WHERE a.id = c.id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cutoff", toUtcOffset(cutoff))
                .addValue("now", toUtcOffset(now))
                .addValue("batchSize", batchSize);

        return jdbcTemplate.update(sql, params);
    }

    /**
     * Quy tắc 5: Xử lý các tệp mồ côi (PROCESSING hoặc AVAILABLE quá 7 ngày không gắn vào tin nhắn sống nào).
     * Chuyển sang DELETED.
     */
    public int cleanupOrphanAssets(Instant orphanCutoff, Instant now, int batchSize) {
        if (batchSize <= 0) {
            return 0;
        }

        String sql = """
            WITH candidate AS (
                SELECT a.id
                FROM media_assets a
                WHERE a.status IN ('PROCESSING', 'AVAILABLE')
                  AND (
                      (a.message_id IS NULL AND a.created_at < :orphanCutoff)
                      OR
                      (a.message_id IS NOT NULL AND EXISTS (
                          SELECT 1 FROM messages m
                          WHERE m.id = a.message_id
                            AND m.deleted_at IS NOT NULL
                            AND m.deleted_at < :orphanCutoff
                      ))
                  )
                ORDER BY a.created_at ASC, a.id ASC
                LIMIT :batchSize
                FOR UPDATE OF a SKIP LOCKED
            )
            UPDATE media_assets a
            SET status = 'DELETED',
                deleted_at = :now,
                locked_until = null,
                updated_at = :now
            FROM candidate c
            WHERE a.id = c.id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orphanCutoff", toUtcOffset(orphanCutoff))
                .addValue("now", toUtcOffset(now))
                .addValue("batchSize", batchSize);

        return jdbcTemplate.update(sql, params);
    }

    /**
     * Quy tắc 2 (Claim): Nhận batch các bản ghi UPLOADING quá hạn (created_at < cutoff).
     * Khóa dòng bằng locked_until và trả về danh sách đối tượng cần dọn dẹp trên Cloud Storage.
     */
    public List<UploadingCleanupTarget> claimExpiredUploading(Instant cutoff, Instant now, Instant lockedUntil, int batchSize) {
        if (batchSize <= 0) {
            return List.of();
        }

        String sql = """
            WITH candidate AS (
                SELECT id
                FROM media_assets
                WHERE status = 'UPLOADING'
                  AND created_at < :cutoff
                  AND (locked_until IS NULL OR locked_until < :now)
                ORDER BY created_at ASC, id ASC
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            UPDATE media_assets a
            SET locked_until = :lockedUntil,
                updated_at = :now
            FROM candidate c
            WHERE a.id = c.id
            RETURNING a.id, a.raw_key, a.upload_id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cutoff", toUtcOffset(cutoff))
                .addValue("now", toUtcOffset(now))
                .addValue("lockedUntil", toUtcOffset(lockedUntil))
                .addValue("batchSize", batchSize);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new UploadingCleanupTarget(
                rs.getLong("id"),
                rs.getString("raw_key"),
                rs.getString("upload_id")
        ));
    }

    /**
     * Quy tắc 6 (Claim): Nhận batch các bản ghi DELETED quá thời gian ân hạn (deleted_at < cutoff).
     * Khóa dòng bằng locked_until và trả về danh sách đối tượng cần xóa triệt để trên Cloud Storage.
     */
    public List<DeletedPurgeTarget> claimPurgeTargets(Instant cutoff, Instant now, Instant lockedUntil, int batchSize) {
        if (batchSize <= 0) {
            return List.of();
        }

        String sql = """
            WITH candidate AS (
                SELECT id
                FROM media_assets
                WHERE status = 'DELETED'
                  AND deleted_at < :cutoff
                  AND (locked_until IS NULL OR locked_until < :now)
                ORDER BY deleted_at ASC, id ASC
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            UPDATE media_assets a
            SET locked_until = :lockedUntil,
                updated_at = :now
            FROM candidate c
            WHERE a.id = c.id
            RETURNING a.id, a.raw_key, a.display_key, a.thumb_key, a.upload_id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cutoff", toUtcOffset(cutoff))
                .addValue("now", toUtcOffset(now))
                .addValue("lockedUntil", toUtcOffset(lockedUntil))
                .addValue("batchSize", batchSize);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new DeletedPurgeTarget(
                rs.getLong("id"),
                rs.getString("raw_key"),
                rs.getString("display_key"),
                rs.getString("thumb_key"),
                rs.getString("upload_id")
        ));
    }

    /**
     * Chốt lô sau khi dọn dẹp Storage cho UPLOADING quá hạn: chuyển sang FAILED với last_error = 'EXPIRED'.
     */
    public int markUploadingExpiredFailed(Collection<Long> ids, Instant now) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        String sql = """
            UPDATE media_assets
            SET status = 'FAILED',
                last_error = 'EXPIRED',
                locked_until = null,
                updated_at = :now
            WHERE id IN (:ids)
              AND status = 'UPLOADING'
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", ids)
                .addValue("now", toUtcOffset(now));

        return jdbcTemplate.update(sql, params);
    }

    /**
     * Chốt lô sau khi xóa sạch object Storage cho DELETED quá hạn: chuyển sang PURGED.
     */
    public int markDeletedPurged(Collection<Long> ids, Instant now) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        String sql = """
            UPDATE media_assets
            SET status = 'PURGED',
                locked_until = null,
                updated_at = :now
            WHERE id IN (:ids)
              AND status = 'DELETED'
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", ids)
                .addValue("now", toUtcOffset(now));

        return jdbcTemplate.update(sql, params);
    }

    /**
     * Giải phóng khóa locked_until cho các ID gặp lỗi Storage để đợt sau retry lại.
     * Tuyệt đối không đánh dấu PURGED hoặc FAILED khi Storage xóa thất bại.
     */
    public int releaseCleanupLocks(Collection<Long> ids, Instant now) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        String sql = """
            UPDATE media_assets
            SET locked_until = null,
                updated_at = :now
            WHERE id IN (:ids)
              AND status IN ('UPLOADING', 'DELETED')
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", ids)
                .addValue("now", toUtcOffset(now));

        return jdbcTemplate.update(sql, params);
    }

    private static OffsetDateTime toUtcOffset(Instant instant) {
        return instant != null ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }
}
