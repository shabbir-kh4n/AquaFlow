package com.hydroledger.backend.dto;

import com.hydroledger.backend.entity.SupportTicketStatus;

import jakarta.validation.constraints.NotNull;

public record SupportTicketStatusUpdateRequest(
        @NotNull(message = "Status is required")
        SupportTicketStatus status
) {
}
