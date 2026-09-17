package com.hydroledger.backend.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record TariffTierRequest(
        @NotNull(message = "Tier minimum is required")
        @DecimalMin(value = "0.0", message = "Tier minimum cannot be negative")
        BigDecimal minUnits,

        BigDecimal maxUnits,

        @NotNull(message = "Tier rate is required")
        @DecimalMin(value = "0.0", message = "Tier rate cannot be negative")
        BigDecimal ratePerUnit
) {
}
