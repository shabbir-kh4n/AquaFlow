package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AlertConfigResponse(
        Long id,
        BigDecimal monthlyThresholdKl,
        BigDecimal outlierStdDevMultiplier,
        boolean thresholdAlertsEnabled,
        boolean outlierAlertsEnabled,
        boolean persisted,
        Instant updatedAt
) {
}
