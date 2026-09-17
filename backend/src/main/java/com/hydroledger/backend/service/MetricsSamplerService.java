package com.hydroledger.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.hydroledger.backend.entity.SystemMetricSample;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemMetricSampleRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MetricsSamplerService {

    private static final Logger log = LoggerFactory.getLogger(MetricsSamplerService.class);
    private static final Duration RETENTION = Duration.ofDays(100);
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final MeterRegistry meterRegistry;
    private final ResidentRepository residentRepository;
    private final CommunityAdminRepository communityAdminRepository;
    private final SystemMetricSampleRepository systemMetricSampleRepository;

    private RequestSnapshot lastSnapshot;

    @Scheduled(fixedRate = 15000)
    public void sample() {
        RequestSnapshot current = captureSnapshot();

        if (lastSnapshot == null) {
            lastSnapshot = current;
            return;
        }

        double elapsedSeconds = Duration.between(lastSnapshot.takenAt(), current.takenAt()).toMillis() / 1000.0;
        if (elapsedSeconds <= 0) {
            lastSnapshot = current;
            return;
        }

        MetricBucket overall = delta(lastSnapshot.overall(), current.overall(), elapsedSeconds);
        MetricBucket reads = delta(lastSnapshot.reads(), current.reads(), elapsedSeconds);
        MetricBucket writes = delta(lastSnapshot.writes(), current.writes(), elapsedSeconds);
        MetricBucket other = delta(lastSnapshot.other(), current.other(), elapsedSeconds);

        SystemMetricSample sampleRow = SystemMetricSample.builder()
                .capturedAt(current.takenAt())
                .opsPerSecond(overall.opsPerSecond())
                .avgLatencyMs(overall.avgLatencyMs())
                .readLatencyMs(reads.avgLatencyMs())
                .writeLatencyMs(writes.avgLatencyMs())
                .otherLatencyMs(other.avgLatencyMs())
                .activeConnections(readActiveConnections())
                .totalKeys((int) (residentRepository.count() + communityAdminRepository.count()))
                .build();

        systemMetricSampleRepository.save(sampleRow);
        lastSnapshot = current;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void pruneOldSamples() {
        Instant cutoff = Instant.now().minus(RETENTION);
        systemMetricSampleRepository.deleteByCapturedAtBefore(cutoff);
        log.info("Pruned system metric samples older than {}", cutoff);
    }

    RequestSnapshot captureSnapshot() {
        Collection<Timer> timers = meterRegistry.find("http.server.requests").timers();

        long overallCount = 0;
        double overallTotalMs = 0;
        long readCount = 0;
        double readTotalMs = 0;
        long writeCount = 0;
        double writeTotalMs = 0;
        long otherCount = 0;
        double otherTotalMs = 0;

        for (Timer timer : timers) {
            long count = timer.count();
            double totalMs = timer.totalTime(TimeUnit.MILLISECONDS);
            overallCount += count;
            overallTotalMs += totalMs;

            String method = timer.getId().getTag("method");
            if (method != null && method.equalsIgnoreCase("GET")) {
                readCount += count;
                readTotalMs += totalMs;
            } else if (method != null && WRITE_METHODS.contains(method.toUpperCase())) {
                writeCount += count;
                writeTotalMs += totalMs;
            } else {
                otherCount += count;
                otherTotalMs += totalMs;
            }
        }

        return new RequestSnapshot(
                now(),
                new RequestCounter(overallCount, overallTotalMs),
                new RequestCounter(readCount, readTotalMs),
                new RequestCounter(writeCount, writeTotalMs),
                new RequestCounter(otherCount, otherTotalMs));
    }

    Instant now() {
        return Instant.now();
    }

    private int readActiveConnections() {
        Gauge gauge = meterRegistry.find("hikaricp.connections.active").gauge();
        return gauge != null ? (int) gauge.value() : 0;
    }

    private static MetricBucket delta(RequestCounter last, RequestCounter current, double elapsedSeconds) {
        long deltaCount = current.count() - last.count();
        double deltaTotalMs = current.totalMs() - last.totalMs();
        if (deltaCount <= 0) {
            return new MetricBucket(0, 0);
        }
        return new MetricBucket(deltaCount / elapsedSeconds, deltaTotalMs / deltaCount);
    }

    record RequestCounter(long count, double totalMs) {
    }

    record RequestSnapshot(Instant takenAt, RequestCounter overall, RequestCounter reads, RequestCounter writes, RequestCounter other) {
    }

    record MetricBucket(double opsPerSecond, double avgLatencyMs) {
    }
}
