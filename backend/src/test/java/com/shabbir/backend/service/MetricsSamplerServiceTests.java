package com.shabbir.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shabbir.backend.entity.SystemMetricSample;
import com.shabbir.backend.repository.CommunityAdminRepository;
import com.shabbir.backend.repository.ResidentRepository;
import com.shabbir.backend.repository.SystemMetricSampleRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class MetricsSamplerServiceTests {

    @Mock
    private ResidentRepository residentRepository;

    @Mock
    private CommunityAdminRepository communityAdminRepository;

    @Mock
    private SystemMetricSampleRepository systemMetricSampleRepository;

    @Test
    void firstTickDoesNotPersistASample() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsSamplerService service = spy(new MetricsSamplerService(
                registry, residentRepository, communityAdminRepository, systemMetricSampleRepository));
        doReturn(Instant.parse("2026-01-01T00:00:00Z")).when(service).now();

        service.sample();

        verify(systemMetricSampleRepository, never()).save(any());
    }

    @Test
    void secondTickComputesDeltaBasedOpsAndLatencySplitByMethod() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Timer getTimer = Timer.builder("http.server.requests").tag("method", "GET").register(registry);
        Timer postTimer = Timer.builder("http.server.requests").tag("method", "POST").register(registry);
        Gauge.builder("hikaricp.connections.active", () -> 3).register(registry);

        MetricsSamplerService service = spy(new MetricsSamplerService(
                registry, residentRepository, communityAdminRepository, systemMetricSampleRepository));
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = t1.plusSeconds(15);
        doReturn(t1).doReturn(t2).when(service).now();

        when(residentRepository.count()).thenReturn(10L);
        when(communityAdminRepository.count()).thenReturn(2L);

        service.sample(); // baseline tick, no data recorded yet

        getTimer.record(Duration.ofMillis(100));
        getTimer.record(Duration.ofMillis(200));
        postTimer.record(Duration.ofMillis(300));

        service.sample(); // real tick, should persist a delta-based sample

        ArgumentCaptor<SystemMetricSample> captor = ArgumentCaptor.forClass(SystemMetricSample.class);
        verify(systemMetricSampleRepository).save(captor.capture());
        SystemMetricSample saved = captor.getValue();

        assertThat(saved.getCapturedAt()).isEqualTo(t2);
        assertThat(saved.getReadLatencyMs()).isEqualTo(150.0);
        assertThat(saved.getWriteLatencyMs()).isEqualTo(300.0);
        assertThat(saved.getOtherLatencyMs()).isEqualTo(0.0);
        assertThat(saved.getAvgLatencyMs()).isEqualTo(200.0);
        assertThat(saved.getOpsPerSecond()).isEqualTo(3.0 / 15.0);
        assertThat(saved.getActiveConnections()).isEqualTo(3);
        assertThat(saved.getTotalKeys()).isEqualTo(12);
    }

    @Test
    void tickWithNoNewRequestsPersistsZeroOpsAndLatency() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Timer.builder("http.server.requests").tag("method", "GET").register(registry);

        MetricsSamplerService service = spy(new MetricsSamplerService(
                registry, residentRepository, communityAdminRepository, systemMetricSampleRepository));
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = t1.plusSeconds(15);
        doReturn(t1).doReturn(t2).when(service).now();

        when(residentRepository.count()).thenReturn(0L);
        when(communityAdminRepository.count()).thenReturn(0L);

        service.sample();
        service.sample();

        ArgumentCaptor<SystemMetricSample> captor = ArgumentCaptor.forClass(SystemMetricSample.class);
        verify(systemMetricSampleRepository).save(captor.capture());
        SystemMetricSample saved = captor.getValue();

        assertThat(saved.getOpsPerSecond()).isEqualTo(0.0);
        assertThat(saved.getAvgLatencyMs()).isEqualTo(0.0);
        assertThat(saved.getActiveConnections()).isEqualTo(0);
    }
}
