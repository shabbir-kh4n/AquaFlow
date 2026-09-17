package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record BillEstimateResponse(
        String periodMonth,
        BigDecimal consumptionUnits,
        BigDecimal basePricePerUnit,
        BigDecimal baseAmount,
        List<TierChargeLine> tierBreakdown,
        BigDecimal tariffAmount,
        List<CommonAreaChargeLine> commonAreaCharges,
        BigDecimal commonAreaAmount,
        BigDecimal totalAmount
) {
}
