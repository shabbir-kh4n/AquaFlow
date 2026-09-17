package com.hydroledger.backend.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.hydroledger.backend.dto.SystemMetricsResponse;
import com.hydroledger.backend.dto.SystemMetricsResponse.MetricPoint;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.service.SystemMetricsService;

@ExtendWith(MockitoExtension.class)
class SystemMetricsServiceTests {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private SystemMetricsService systemMetricsService;

    @Test
    void unknownRangeThrowsBadRequest() {
        assertThatThrownBy(() -> systemMetricsService.getMetrics("bogus"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("bogus");
    }

    @Test
    @SuppressWarnings("unchecked")
    void knownRangeQueriesWithMatchingBucketInterval() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
                .thenReturn(List.<MetricPoint>of());

        SystemMetricsResponse response = systemMetricsService.getMetrics("1h");

        assertThat(response.range()).isEqualTo("1h");
        assertThat(response.points()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void summaryComputesMinAvgMaxLastFromPoints() {
        MetricPoint p1 = new MetricPoint(Instant.parse("2026-01-01T00:00:00Z"), 1.0, 10.0, 5.0, 15.0, 2.0, 1.0, 3.0);
        MetricPoint p2 = new MetricPoint(Instant.parse("2026-01-01T00:01:00Z"), 3.0, 20.0, 8.0, 25.0, 4.0, 2.0, 5.0);
        MetricPoint p3 = new MetricPoint(Instant.parse("2026-01-01T00:02:00Z"), 2.0, 5.0, 3.0, 10.0, 1.0, 0.0, 4.0);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
                .thenReturn(List.of(p1, p2, p3));

        SystemMetricsResponse response = systemMetricsService.getMetrics("5m");

        assertThat(response.opsPerSecond().min()).isEqualTo(1.0);
        assertThat(response.opsPerSecond().max()).isEqualTo(3.0);
        assertThat(response.opsPerSecond().avg()).isEqualTo(2.0);
        assertThat(response.opsPerSecond().last()).isEqualTo(2.0);
    }
}
