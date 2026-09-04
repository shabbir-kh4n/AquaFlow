package com.shabbir.backend.dto;

import java.math.BigDecimal;

import com.shabbir.backend.entity.TariffTier;

public record TariffTierResponse(
        Integer tierOrder,
        BigDecimal minUnits,
        BigDecimal maxUnits,
        BigDecimal ratePerUnit
) {
    public static TariffTierResponse from(TariffTier tier) {
        return new TariffTierResponse(tier.getTierOrder(), tier.getMinUnits(), tier.getMaxUnits(), tier.getRatePerUnit());
    }
}
