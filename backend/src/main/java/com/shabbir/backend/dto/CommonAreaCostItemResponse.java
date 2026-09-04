package com.shabbir.backend.dto;

import java.math.BigDecimal;

import com.shabbir.backend.entity.CommonAreaCostItem;

public record CommonAreaCostItemResponse(
        Long id,
        String label,
        BigDecimal monthlyAmount,
        boolean active
) {
    public static CommonAreaCostItemResponse from(CommonAreaCostItem item) {
        return new CommonAreaCostItemResponse(item.getId(), item.getLabel(), item.getMonthlyAmount(), item.isActive());
    }
}
