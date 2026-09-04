package com.shabbir.backend.dto;

import java.time.LocalDate;

import com.shabbir.backend.entity.MeterStatus;

import jakarta.validation.constraints.NotBlank;

public record WaterMeterRequest(
        @NotBlank(message = "Meter number is required")
        String meterNumber,

        LocalDate installedAt,

        MeterStatus status
) {
}
