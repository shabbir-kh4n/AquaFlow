package com.hydroledger.backend.dto;

import java.math.BigDecimal;

public record CommonAreaChargeLine(
        Long costItemId,
        String label,
        BigDecimal amount
) {
}
