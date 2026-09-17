package com.hydroledger.backend.dto;

import com.hydroledger.backend.entity.AlertStatus;

import jakarta.validation.constraints.NotNull;

public record AlertStatusUpdateRequest(
        @NotNull(message = "Status is required")
        AlertStatus status
) {
}
