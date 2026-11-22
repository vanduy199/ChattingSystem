package com.ChattingSystem.media.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaAssetClaimDaoUnitTest {

    @Test
    @DisplayName("claimJobs: khi limit <= 0 trả về List rỗng ngay, không query DB")
    void claimJobs_limitZeroOrNegative_shouldReturnEmptyList() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MediaAssetClaimDao dao = new MediaAssetClaimDao(jdbcTemplate);

        List<Long> result = dao.claimJobs(0, Instant.now(), Instant.now().plusSeconds(600), 4);

        assertThat(result).isEmpty();
        verify(jdbcTemplate, never()).query(any(String.class), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    @DisplayName("claimJobs: gửi tham số now và lockedUntil dưới dạng OffsetDateTime UTC để tương thích TIMESTAMPTZ")
    void claimJobs_shouldPassOffsetDateTimeInUtc() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MediaAssetClaimDao dao = new MediaAssetClaimDao(jdbcTemplate);

        Instant now = Instant.parse("2026-11-21T10:30:00Z");
        Instant lockedUntil = Instant.parse("2026-11-21T10:42:00Z");

        ArgumentCaptor<MapSqlParameterSource> paramCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        when(jdbcTemplate.query(any(String.class), paramCaptor.capture(), any(RowMapper.class)))
                .thenReturn(List.of(100L));

        List<Long> result = dao.claimJobs(2, now, lockedUntil, 4);

        assertThat(result).containsExactly(100L);
        MapSqlParameterSource params = paramCaptor.getValue();
        assertThat(params.getValue("limit")).isEqualTo(2);
        assertThat(params.getValue("maxAttempts")).isEqualTo(4);

        Object nowVal = params.getValue("now");
        assertThat(nowVal).isInstanceOf(OffsetDateTime.class);
        OffsetDateTime nowOdt = (OffsetDateTime) nowVal;
        assertThat(nowOdt.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(nowOdt.toInstant()).isEqualTo(now);

        Object lockedVal = params.getValue("lockedUntil");
        assertThat(lockedVal).isInstanceOf(OffsetDateTime.class);
        OffsetDateTime lockedOdt = (OffsetDateTime) lockedVal;
        assertThat(lockedOdt.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(lockedOdt.toInstant()).isEqualTo(lockedUntil);
    }
}
