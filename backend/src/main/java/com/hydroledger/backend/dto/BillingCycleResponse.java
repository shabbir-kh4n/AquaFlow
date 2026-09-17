package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.hydroledger.backend.entity.BillingCycleStatus;

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
