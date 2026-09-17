package com.hydroledger.backend.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

public record AlertConfigRequest(
        BigDecimal monthlyThresholdKl,
        @NotNull(message = "Standard deviation multiplier is required")
        BigDecimal outlierStdDevMultiplier,
        boolean thresholdAlertsEnabled,
        boolean outlierAlertsEnabled
) {
}
