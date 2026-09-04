package com.shabbir.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.shabbir.backend.entity.ReadingSource;
import com.shabbir.backend.entity.WaterUsageReading;

public record WaterUsageReadingResponse(
        Long id,
        String meterNumber,
        LocalDate readingDate,
        BigDecimal readingValue,
        ReadingSource source,
        BigDecimal consumption,
        Instant createdAt
) {
    public static WaterUsageReadingResponse from(WaterUsageReading reading, BigDecimal consumption) {
        return new WaterUsageReadingResponse(
                reading.getId(),
                reading.getMeter().getMeterNumber(),
                reading.getReadingDate(),
                reading.getReadingValue(),
                reading.getSource(),
                consumption,
                reading.getCreatedAt()
        );
    }
}
