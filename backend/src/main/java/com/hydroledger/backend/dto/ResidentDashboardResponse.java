package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ResidentDashboardResponse(
        String communityName,
        String houseNumber,
        String meterNumber,
        BigDecimal latestReadingValue,
        LocalDate latestReadingDate
) {
}
