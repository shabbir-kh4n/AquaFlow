package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TariffPlanResponse(
        Long id,
        List<TariffTierResponse> tiers,
        BigDecimal basePricePerUnit,
        boolean persisted,
        Instant updatedAt
) {
}
