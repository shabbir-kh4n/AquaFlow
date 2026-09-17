package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record BillEstimateResponse(
        String periodMonth,
        BigDecimal consumptionUnits,
        List<TierChargeLine> tierBreakdown,
        BigDecimal totalAmount
) {
}
