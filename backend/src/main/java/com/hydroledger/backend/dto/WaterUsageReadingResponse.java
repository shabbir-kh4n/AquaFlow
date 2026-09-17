package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import com.hydroledger.backend.entity.ReadingSource;
import com.hydroledger.backend.entity.WaterUsageReading;

public record WaterUsageReadingResponse(
        Long id,
        String meterNumber,
        LocalDate readingDate,
        BigDecimal readingValue,
        ReadingSource source,
        BigDecimal consumption,
        Instant createdAt,
        // The month this reading's consumption actually accrued in, not the month it was recorded in:
        // consumption is the delta since the previous reading, so it belongs to the period between the
        // two readings (attributed here to the earlier one), e.g. a reading taken Feb 5 following a Jan 5
        // reading reports January's usage, even though the reading itself is dated in February.
        String consumptionMonth
) {
    public static WaterUsageReadingResponse from(WaterUsageReading reading, BigDecimal consumption, LocalDate previousReadingDate) {
        LocalDate attributionDate = previousReadingDate != null ? previousReadingDate : reading.getReadingDate();
        return new WaterUsageReadingResponse(
                reading.getId(),
                reading.getMeter().getMeterNumber(),
                reading.getReadingDate(),
                reading.getReadingValue(),
                reading.getSource(),
                consumption,
                reading.getCreatedAt(),
                YearMonth.from(attributionDate).toString()
        );
    }
}
