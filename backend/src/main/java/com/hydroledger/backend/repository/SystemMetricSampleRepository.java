package com.hydroledger.backend.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.SystemMetricSample;

public interface SystemMetricSampleRepository extends JpaRepository<SystemMetricSample, Long> {

    void deleteByCapturedAtBefore(Instant cutoff);
}
