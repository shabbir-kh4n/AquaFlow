package com.hydroledger.backend.dto;

import java.time.Instant;
import java.util.List;

public record ResidentRecommendationResponse(
        List<String> tips,
        Instant generatedAt,
        String sourcePeriodMonth
) {
}
