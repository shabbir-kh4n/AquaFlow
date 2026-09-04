package com.shabbir.backend.dto;

import java.math.BigDecimal;

public record TierChargeLine(
        Integer tierOrder,
        BigDecimal unitsInTier,
        BigDecimal ratePerUnit,
        BigDecimal amount
) {
}
