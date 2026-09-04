package com.shabbir.backend.dto;

import java.time.Instant;
import java.util.List;

public record TariffPlanResponse(
        Long id,
        List<TariffTierResponse> tiers,
        boolean persisted,
        Instant updatedAt
) {
}
