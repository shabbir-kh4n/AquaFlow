package com.shabbir.backend.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.shabbir.backend.dto.SystemMetricsResponse;
import com.shabbir.backend.dto.SystemMetricsResponse.MetricPoint;
import com.shabbir.backend.dto.SystemMetricsResponse.MetricSummary;
import com.shabbir.backend.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SystemMetricsService {

    private static final Map<String, RangeSpec> RANGES = Map.of(
            "5m", new RangeSpec(Duration.ofMinutes(5), "15 seconds"),
            "15m", new RangeSpec(Duration.ofMinutes(15), "15 seconds"),
            "1h", new RangeSpec(Duration.ofHours(1), "1 minute"),
            "1d", new RangeSpec(Duration.ofDays(1), "20 minutes"),
            "1w", new RangeSpec(Duration.ofDays(7), "2 hours"),
            "1mo", new RangeSpec(Duration.ofDays(30), "8 hours"),
            "3mo", new RangeSpec(Duration.ofDays(90), "1 day")
    );

    private static final String BUCKET_SQL = """
            SELECT date_bin(CAST(? AS interval), captured_at, TIMESTAMP '2000-01-01') AS bucket_ts,
                   AVG(ops_per_second) AS ops,
                   AVG(avg_latency_ms) AS lat,
                   AVG(read_latency_ms) AS read_lat,
                   AVG(write_latency_ms) AS write_lat,
                   AVG(other_latency_ms) AS other_lat,
                   AVG(active_connections) AS conns,
                   MAX(total_keys) AS keys
            FROM system_metric_samples
            WHERE captured_at >= ?
            GROUP BY bucket_ts
            ORDER BY bucket_ts
            """;

    private final JdbcTemplate jdbcTemplate;

    public SystemMetricsResponse getMetrics(String range) {
        RangeSpec spec = RANGES.get(range);
        if (spec == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown range: " + range);
        }

        Instant from = Instant.now().minus(spec.lookback());

        List<MetricPoint> points = jdbcTemplate.query(
                BUCKET_SQL,
                (rs, rowNum) -> new MetricPoint(
                        rs.getTimestamp("bucket_ts").toInstant(),
                        rs.getDouble("ops"),
                        rs.getDouble("lat"),
                        rs.getDouble("read_lat"),
                        rs.getDouble("write_lat"),
                        rs.getDouble("other_lat"),
                        rs.getDouble("conns"),
                        rs.getDouble("keys")
                ),
                spec.bucketInterval(), Timestamp.from(from));

        return new SystemMetricsResponse(
                range,
                points,
                summarize(points, MetricPoint::opsPerSecond),
                summarize(points, MetricPoint::latencyMs),
                summarize(points, MetricPoint::readLatencyMs),
                summarize(points, MetricPoint::writeLatencyMs),
                summarize(points, MetricPoint::otherLatencyMs),
                summarize(points, MetricPoint::connections),
                summarize(points, MetricPoint::totalKeys)
        );
    }

    private MetricSummary summarize(List<MetricPoint> points, ToDoubleFunction<MetricPoint> extractor) {
        if (points.isEmpty()) {
            return new MetricSummary(0, 0, 0, 0);
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0;
        for (MetricPoint point : points) {
            double value = extractor.applyAsDouble(point);
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
        }

        double last = extractor.applyAsDouble(points.get(points.size() - 1));
        return new MetricSummary(min, sum / points.size(), max, last);
    }

    private record RangeSpec(Duration lookback, String bucketInterval) {
    }
}
