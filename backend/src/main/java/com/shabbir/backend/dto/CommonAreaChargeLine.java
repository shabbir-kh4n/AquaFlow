package com.shabbir.backend.dto;

import java.math.BigDecimal;

public record CommonAreaChargeLine(
        Long costItemId,
        String label,
        BigDecimal amount
) {
}
