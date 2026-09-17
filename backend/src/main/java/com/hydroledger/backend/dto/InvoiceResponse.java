package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceResponse(
        Long id,
        Long householdId,
        String houseNumber,
        String residentName,
        String meterNumber,
        String periodMonth,
        BigDecimal consumptionUnits,
        BigDecimal totalAmount,
        BigDecimal tariffAmount,
        BigDecimal commonAreaAmount,
        Instant generatedAt,
        boolean paid,
        Instant paidAt,
        List<TierChargeLine> lineItems,
        List<CommonAreaChargeLine> commonAreaCharges
) {
}
