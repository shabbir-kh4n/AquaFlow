package com.hydroledger.backend.dto;

import java.math.BigDecimal;

public record ConsumptionComparisonPoint(
        String month,
        BigDecimal household,
        BigDecimal apartmentAverage,
        BigDecimal similarSizedAverage
) {
}
