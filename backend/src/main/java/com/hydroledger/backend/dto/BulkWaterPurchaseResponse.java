package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.hydroledger.backend.entity.BulkWaterPurchase;

public record BulkWaterPurchaseResponse(
        Long id,
        LocalDate purchaseDate,
        String source,
        BigDecimal volumeLiters,
        BigDecimal unitCost,
        BigDecimal totalCost,
        String notes,
        Instant createdAt
) {
    public static BulkWaterPurchaseResponse from(BulkWaterPurchase purchase) {
        return new BulkWaterPurchaseResponse(
                purchase.getId(),
                purchase.getPurchaseDate(),
                purchase.getSource(),
                purchase.getVolumeLiters(),
                purchase.getUnitCost(),
                purchase.getTotalCost(),
                purchase.getNotes(),
                purchase.getCreatedAt()
        );
    }
}
