package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

public record WaterUsageReadingRequest(
        @NotBlank(message = "Meter number is required")
        String meterNumber,

        @NotNull(message = "Reading date is required")
        @PastOrPresent(message = "Reading date cannot be in the future")
        LocalDate readingDate,

        @NotNull(message = "Reading value is required")
        @DecimalMin(value = "0.0", message = "Reading value cannot be negative")
        BigDecimal readingValue
) {
}
