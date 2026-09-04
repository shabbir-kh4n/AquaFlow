package com.shabbir.backend.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.shabbir.backend.entity.MeterStatus;
import com.shabbir.backend.entity.WaterMeter;

public record WaterMeterResponse(
        Long id,
        String meterNumber,
        Long householdId,
        String houseNumber,
        LocalDate installedAt,
        MeterStatus status,
        Instant createdAt
) {
    public static WaterMeterResponse from(WaterMeter meter) {
        return new WaterMeterResponse(
                meter.getId(),
                meter.getMeterNumber(),
                meter.getHousehold() != null ? meter.getHousehold().getId() : null,
                meter.getHousehold() != null ? meter.getHousehold().getHouseNumber() : null,
                meter.getInstalledAt(),
                meter.getStatus(),
                meter.getCreatedAt()
        );
    }
}
