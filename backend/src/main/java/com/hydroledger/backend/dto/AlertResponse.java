package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.hydroledger.backend.entity.AlertStatus;
import com.hydroledger.backend.entity.AlertType;

public record AlertResponse(
        Long id,
        Long householdId,
        String houseNumber,
        String residentName,
        String meterNumber,
        AlertType alertType,
        String periodMonth,
        BigDecimal consumptionValue,
        BigDecimal referenceValue,
        String message,
        AlertStatus status,
        Instant createdAt,
        Instant resolvedAt
) {
}
