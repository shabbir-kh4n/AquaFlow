package com.shabbir.backend.dto;

import com.shabbir.backend.entity.AlertStatus;

import jakarta.validation.constraints.NotNull;

public record AlertStatusUpdateRequest(
        @NotNull(message = "Status is required")
        AlertStatus status
) {
}
