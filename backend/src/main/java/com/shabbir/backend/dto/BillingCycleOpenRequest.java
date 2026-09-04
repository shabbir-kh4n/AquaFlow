package com.shabbir.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BillingCycleOpenRequest(
        @NotBlank(message = "periodMonth is required")
        @Pattern(regexp = "\\d{4}-\\d{2}", message = "periodMonth must be in YYYY-MM format")
        String periodMonth
) {
}
