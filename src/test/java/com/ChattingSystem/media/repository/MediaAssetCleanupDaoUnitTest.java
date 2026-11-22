package com.ChattingSystem.media.repository;

import com.ChattingSystem.media.repository.dto.DeletedPurgeTarget;
import com.ChattingSystem.media.repository.dto.UploadingCleanupTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("Kiểm thử MediaAssetCleanupDao (DAO dọn dẹp rác & PostgreSQL TIMESTAMPTZ)")
class MediaAssetCleanupDaoUnitTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private MediaAssetCleanupDao dao;

    private final Instant now = Instant.parse("2026-11-21T10:00:00Z");
    private final Instant cutoff = Instant.parse("2026-11-20T10:00:00Z");
    private final Instant lockedUntil = Instant.parse("2026-11-21T10:15:00Z");

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        dao = new MediaAssetCleanupDao(jdbcTemplate);
    }

    @Test
    @DisplayName("tryAcquireDbCleanupLock: trả về true khi lấy được advisory lock")
    void tryAcquireDbCleanupLock_acquired_returnsTrue() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);

        boolean result = dao.tryAcquireDbCleanupLock();

        assertThat(result).isTrue();
        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(anyString(), captor.capture(), eq(Boolean.class));
        assertThat(captor.getValue().getValue("lockKey")).isEqualTo(MediaAssetCleanupDao.DB_CLEANUP_LOCK_KEY);
    }

    @Test
    @DisplayName("tryAcquireDbCleanupLock: trả về false khi không lấy được advisory lock")
    void tryAcquireDbCleanupLock_notAcquired_returnsFalse() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(Boolean.FALSE);

        boolean result = dao.tryAcquireDbCleanupLock();

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("cleanupCreatingTimeout (Quy tắc 1): batchSize <= 0 trả về 0 ngay không gọi DB")
    void cleanupCreatingTimeout_batchSizeZeroOrNegative_returnsZero() {
        int updated = dao.cleanupCreatingTimeout(cutoff, now, 0);

        assertThat(updated).isZero();
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("cleanupCreatingTimeout (Quy tắc 1): truyền OffsetDateTime UTC cho cutoff và now")
    void cleanupCreatingTimeout_validBatch_passesUtcParameters() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(5);

        int updated = dao.cleanupCreatingTimeout(cutoff, now, 50);

        assertThat(updated).isEqualTo(5);
        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());

        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValue("batchSize")).isEqualTo(50);
        assertUtcOffset(params.getValue("cutoff"), cutoff);
        assertUtcOffset(params.getValue("now"), now);
    }

    @Test
    @DisplayName("revertCompletingTimeout (Quy tắc 3): batchSize <= 0 trả về 0 ngay không gọi DB")
    void revertCompletingTimeout_batchSizeZeroOrNegative_returnsZero() {
        int updated = dao.revertCompletingTimeout(cutoff, now, -1);

        assertThat(updated).isZero();
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("revertCompletingTimeout (Quy tắc 3): truyền OffsetDateTime UTC cho cutoff và now")
    void revertCompletingTimeout_validBatch_passesUtcParameters() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(3);

        int updated = dao.revertCompletingTimeout(cutoff, now, 20);

        assertThat(updated).isEqualTo(3);
        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());

        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValue("batchSize")).isEqualTo(20);
        assertUtcOffset(params.getValue("cutoff"), cutoff);
        assertUtcOffset(params.getValue("now"), now);
    }

    @Test
    @DisplayName("cleanupFailedAndBlocked (Quy tắc 4): batchSize <= 0 trả về 0 ngay không gọi DB")
    void cleanupFailedAndBlocked_batchSizeZeroOrNegative_returnsZero() {
        int updated = dao.cleanupFailedAndBlocked(cutoff, now, 0);

        assertThat(updated).isZero();
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("cleanupFailedAndBlocked (Quy tắc 4): truyền OffsetDateTime UTC cho cutoff và now")
    void cleanupFailedAndBlocked_validBatch_passesUtcParameters() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(7);

        int updated = dao.cleanupFailedAndBlocked(cutoff, now, 100);

        assertThat(updated).isEqualTo(7);
        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());

        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValue("batchSize")).isEqualTo(100);
        assertUtcOffset(params.getValue("cutoff"), cutoff);
        assertUtcOffset(params.getValue("now"), now);
    }

    @Test
    @DisplayName("cleanupOrphanAssets (Quy tắc 5): batchSize <= 0 trả về 0 ngay không gọi DB")
    void cleanupOrphanAssets_batchSizeZeroOrNegative_returnsZero() {
        int updated = dao.cleanupOrphanAssets(cutoff, now, 0);

        assertThat(updated).isZero();
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("cleanupOrphanAssets (Quy tắc 5): truyền OffsetDateTime UTC cho orphanCutoff và now")
    void cleanupOrphanAssets_validBatch_passesUtcParameters() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(12);

        int updated = dao.cleanupOrphanAssets(cutoff, now, 200);

        assertThat(updated).isEqualTo(12);
        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());

        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValue("batchSize")).isEqualTo(200);
        assertUtcOffset(params.getValue("orphanCutoff"), cutoff);
        assertUtcOffset(params.getValue("now"), now);
    }

    @Test
    @DisplayName("claimExpiredUploading (Quy tắc 2): batchSize <= 0 trả về List rỗng ngay không gọi DB")
    void claimExpiredUploading_batchSizeZeroOrNegative_returnsEmpty() {
        List<UploadingCleanupTarget> list = dao.claimExpiredUploading(cutoff, now, lockedUntil, 0);

        assertThat(list).isEmpty();
        verify(jdbcTemplate, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    @DisplayName("claimExpiredUploading (Quy tắc 2): truyền OffsetDateTime UTC và ánh xạ đúng UploadingCleanupTarget")
    @SuppressWarnings("unchecked")
    void claimExpiredUploading_validBatch_mapsTargetsAndPassesUtcParameters() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(101L);
        when(rs.getString("raw_key")).thenReturn("raw/test.jpg");
        when(rs.getString("upload_id")).thenReturn("upload-xyz");

        ArgumentCaptor<RowMapper<UploadingCleanupTarget>> mapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        ArgumentCaptor<MapSqlParameterSource> paramCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);

        when(jdbcTemplate.query(anyString(), paramCaptor.capture(), mapperCaptor.capture()))
                .thenAnswer(inv -> {
                    RowMapper<UploadingCleanupTarget> mapper = inv.getArgument(2);
                    UploadingCleanupTarget item = mapper.mapRow(rs, 1);
                    return List.of(item);
                });

        List<UploadingCleanupTarget> result = dao.claimExpiredUploading(cutoff, now, lockedUntil, 50);

        assertThat(result).hasSize(1);
        UploadingCleanupTarget target = result.get(0);
        assertThat(target.id()).isEqualTo(101L);
        assertThat(target.rawKey()).isEqualTo("raw/test.jpg");
        assertThat(target.uploadId()).isEqualTo("upload-xyz");

        MapSqlParameterSource params = paramCaptor.getValue();
        assertThat(params.getValue("batchSize")).isEqualTo(50);
        assertUtcOffset(params.getValue("cutoff"), cutoff);
        assertUtcOffset(params.getValue("now"), now);
        assertUtcOffset(params.getValue("lockedUntil"), lockedUntil);
    }

    @Test
    @DisplayName("claimPurgeTargets (Quy tắc 6): batchSize <= 0 trả về List rỗng ngay không gọi DB")
    void claimPurgeTargets_batchSizeZeroOrNegative_returnsEmpty() {
        List<DeletedPurgeTarget> list = dao.claimPurgeTargets(cutoff, now, lockedUntil, 0);

        assertThat(list).isEmpty();
        verify(jdbcTemplate, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    @DisplayName("claimPurgeTargets (Quy tắc 6): truyền OffsetDateTime UTC và ánh xạ đúng DeletedPurgeTarget")
    @SuppressWarnings("unchecked")
    void claimPurgeTargets_validBatch_mapsTargetsAndPassesUtcParameters() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(202L);
        when(rs.getString("raw_key")).thenReturn("raw/202.jpg");
        when(rs.getString("display_key")).thenReturn("display/202.webp");
        when(rs.getString("thumb_key")).thenReturn("thumb/202.webp");
        when(rs.getString("upload_id")).thenReturn("up-202");

        ArgumentCaptor<RowMapper<DeletedPurgeTarget>> mapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        ArgumentCaptor<MapSqlParameterSource> paramCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);

        when(jdbcTemplate.query(anyString(), paramCaptor.capture(), mapperCaptor.capture()))
                .thenAnswer(inv -> {
                    RowMapper<DeletedPurgeTarget> mapper = inv.getArgument(2);
                    DeletedPurgeTarget item = mapper.mapRow(rs, 1);
                    return List.of(item);
                });

        List<DeletedPurgeTarget> result = dao.claimPurgeTargets(cutoff, now, lockedUntil, 30);

        assertThat(result).hasSize(1);
        DeletedPurgeTarget target = result.get(0);
        assertThat(target.id()).isEqualTo(202L);
        assertThat(target.rawKey()).isEqualTo("raw/202.jpg");
        assertThat(target.displayKey()).isEqualTo("display/202.webp");
        assertThat(target.thumbKey()).isEqualTo("thumb/202.webp");
        assertThat(target.uploadId()).isEqualTo("up-202");

        MapSqlParameterSource params = paramCaptor.getValue();
        assertThat(params.getValue("batchSize")).isEqualTo(30);
        assertUtcOffset(params.getValue("cutoff"), cutoff);
        assertUtcOffset(params.getValue("now"), now);
        assertUtcOffset(params.getValue("lockedUntil"), lockedUntil);
    }

    @Test
    @DisplayName("markUploadingExpiredFailed: ids rỗng hoặc null trả về 0 ngay không gọi DB")
    void markUploadingExpiredFailed_emptyIds_returnsZero() {
        int updated1 = dao.markUploadingExpiredFailed(null, now);
        int updated2 = dao.markUploadingExpiredFailed(List.of(), now);

        assertThat(updated1).isZero();
        assertThat(updated2).isZero();
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("markUploadingExpiredFailed: truyền ids và OffsetDateTime UTC")
    void markUploadingExpiredFailed_validIds_passesParameters() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(2);

        int updated = dao.markUploadingExpiredFailed(List.of(10L, 20L), now);

        assertThat(updated).isEqualTo(2);
        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());

        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValue("ids")).isEqualTo(List.of(10L, 20L));
        assertUtcOffset(params.getValue("now"), now);
    }

    @Test
    @DisplayName("markDeletedPurged: ids rỗng hoặc null trả về 0 ngay không gọi DB")
    void markDeletedPurged_emptyIds_returnsZero() {
        int updated1 = dao.markDeletedPurged(null, now);
        int updated2 = dao.markDeletedPurged(List.of(), now);

        assertThat(updated1).isZero();
        assertThat(updated2).isZero();
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("markDeletedPurged: truyền ids và OffsetDateTime UTC")
    void markDeletedPurged_validIds_passesParameters() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(4);

        int updated = dao.markDeletedPurged(List.of(11L, 22L), now);

        assertThat(updated).isEqualTo(4);
        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());

        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValue("ids")).isEqualTo(List.of(11L, 22L));
        assertUtcOffset(params.getValue("now"), now);
    }

    @Test
    @DisplayName("releaseCleanupLocks: ids rỗng hoặc null trả về 0 ngay không gọi DB")
    void releaseCleanupLocks_emptyIds_returnsZero() {
        int updated1 = dao.releaseCleanupLocks(null, now);
        int updated2 = dao.releaseCleanupLocks(List.of(), now);

        assertThat(updated1).isZero();
        assertThat(updated2).isZero();
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("releaseCleanupLocks: truyền ids và OffsetDateTime UTC")
    void releaseCleanupLocks_validIds_passesParameters() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        int updated = dao.releaseCleanupLocks(List.of(99L), now);

        assertThat(updated).isEqualTo(1);
        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());

        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValue("ids")).isEqualTo(List.of(99L));
        assertUtcOffset(params.getValue("now"), now);
    }

    private void assertUtcOffset(Object obj, Instant expectedInstant) {
        assertThat(obj).isInstanceOf(OffsetDateTime.class);
        OffsetDateTime odt = (OffsetDateTime) obj;
        assertThat(odt.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(odt.toInstant()).isEqualTo(expectedInstant);
    }
}
