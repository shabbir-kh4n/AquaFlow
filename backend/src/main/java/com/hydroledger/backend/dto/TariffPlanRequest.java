package com.hydroledger.backend.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record TariffPlanRequest(
        @NotEmpty(message = "At least one tier is required")
        List<@Valid TariffTierRequest> tiers
) {
}
