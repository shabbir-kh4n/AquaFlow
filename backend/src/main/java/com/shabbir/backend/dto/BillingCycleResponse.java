package com.shabbir.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.shabbir.backend.entity.BillingCycleStatus;

public record BillingCycleResponse(
        Long id,
        String periodMonth,
        BillingCycleStatus status,
        Instant openedAt,
        Instant finalizedAt,
        Instant archivedAt,
        int invoiceCount,
        BigDecimal totalBilled
) {
}
