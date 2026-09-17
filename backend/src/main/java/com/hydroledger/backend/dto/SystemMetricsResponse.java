package com.hydroledger.backend.dto;

import java.time.Instant;
import java.util.List;

public record SystemMetricsResponse(
        String range,
        List<MetricPoint> points,
        MetricSummary opsPerSecond,
        MetricSummary latencyMs,
        MetricSummary readLatencyMs,
        MetricSummary writeLatencyMs,
        MetricSummary otherLatencyMs,
        MetricSummary connections,
        MetricSummary totalKeys
) {
    public record MetricPoint(
            Instant timestamp,
            double opsPerSecond,
            double latencyMs,
            double readLatencyMs,
            double writeLatencyMs,
            double otherLatencyMs,
            double connections,
            double totalKeys
    ) {
    }

    public record MetricSummary(double min, double avg, double max, double last) {
    }
}
