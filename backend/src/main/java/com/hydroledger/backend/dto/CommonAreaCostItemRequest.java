package com.hydroledger.backend.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CommonAreaCostItemRequest(
        @NotBlank(message = "Cost item label is required")
        String label,

        @NotNull(message = "Monthly amount is required")
        @DecimalMin(value = "0.01", message = "Monthly amount must be greater than 0")
        BigDecimal monthlyAmount,

        boolean active
) {
}
