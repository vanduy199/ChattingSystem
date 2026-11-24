package com.ChattingSystem.media.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class MediaAssetClaimDao {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MediaAssetClaimDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Claim tối đa {@code limit} job đang ở trạng thái PROCESSING bằng PostgreSQL
     * {@code FOR UPDATE SKIP LOCKED ... RETURNING id}.
     * <p>
     * Cập nhật nguyên tử:
     * - {@code locked_until = :lockedUntil} (thời điểm hết hạn giữ khóa nếu worker chết đột ngột)
     * - {@code attempts = attempts + 1}
     * - {@code updated_at = :now}
     *
     * @param limit       Số lượng slot khả dụng cần claim
     * @param now         Thời điểm hiện tại
     * @param lockedUntil Thời điểm hết hạn khóa (now + jobTimeout + 2 phút buffer)
     * @param maxAttempts Số lần thử tối đa cho phép
     * @return Danh sách ID của các bản ghi media_assets đã được claim thành công
     */
    public List<Long> claimJobs(int limit, Instant now, Instant lockedUntil, int maxAttempts) {
        if (limit <= 0) {
            return List.of();
        }

        String sql = """
            WITH candidate AS (
                SELECT id
                FROM media_assets
                WHERE status = 'PROCESSING'
                  AND next_run_at <= :now
                  AND (locked_until IS NULL OR locked_until < :now)
                  AND attempts < :maxAttempts
                ORDER BY next_run_at ASC, id ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            UPDATE media_assets a
            SET locked_until = :lockedUntil,
                attempts = a.attempts + 1,
                updated_at = :now
            FROM candidate c
            WHERE a.id = c.id
            RETURNING a.id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .addValue("lockedUntil", OffsetDateTime.ofInstant(lockedUntil, ZoneOffset.UTC))
                .addValue("maxAttempts", maxAttempts)
                .addValue("limit", limit);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getLong("id"));
    }
}
