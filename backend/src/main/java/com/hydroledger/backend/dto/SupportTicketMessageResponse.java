package com.hydroledger.backend.dto;

import java.time.Instant;

import com.hydroledger.backend.entity.SupportTicketRole;

public record SupportTicketMessageResponse(
        Long id,
        SupportTicketRole senderRole,
        String senderName,
        String message,
        Instant createdAt
) {
}
