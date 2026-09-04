package com.shabbir.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BulkWaterPurchaseRequest(
        @NotNull(message = "Purchase date is required")
        LocalDate purchaseDate,

        @NotBlank(message = "Source / supplier is required")
        String source,

        @NotNull(message = "Volume is required")
        @DecimalMin(value = "0.01", message = "Volume must be greater than 0")
        BigDecimal volumeLiters,

        @NotNull(message = "Unit cost is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Unit cost must be greater than 0")
        BigDecimal unitCost,

        String notes
) {
}
