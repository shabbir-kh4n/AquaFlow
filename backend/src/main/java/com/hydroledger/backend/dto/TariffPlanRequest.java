package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record TariffPlanRequest(
        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.0", message = "Base price cannot be negative")
        BigDecimal basePricePerUnit,

        @NotEmpty(message = "At least one tier is required")
        List<@Valid TariffTierRequest> tiers
) {
}
