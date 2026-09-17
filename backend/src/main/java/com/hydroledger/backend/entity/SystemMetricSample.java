package com.hydroledger.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "system_metric_samples")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemMetricSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant capturedAt;

    @Column(nullable = false)
    private double opsPerSecond;

    @Column(nullable = false)
    private double avgLatencyMs;

    @Column(nullable = false)
    private double readLatencyMs;

    @Column(nullable = false)
    private double writeLatencyMs;

    @Column(nullable = false)
    private double otherLatencyMs;

    @Column(nullable = false)
    private int activeConnections;

    @Column(nullable = false)
    private int totalKeys;
}
