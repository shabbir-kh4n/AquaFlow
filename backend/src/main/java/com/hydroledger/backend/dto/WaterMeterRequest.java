package com.hydroledger.backend.dto;

import java.time.LocalDate;

import com.hydroledger.backend.entity.MeterStatus;

import jakarta.validation.constraints.NotBlank;

public record WaterMeterRequest(
        @NotBlank(message = "Meter number is required")
        String meterNumber,

        LocalDate installedAt,

        MeterStatus status
) {
}
