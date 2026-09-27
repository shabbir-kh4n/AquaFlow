package com.hydroledger.backend.dto;

import java.time.Instant;

import com.hydroledger.backend.entity.SupportTicketRole;
import com.hydroledger.backend.entity.SupportTicketStatus;

public record SupportTicketResponse(
        Long id,
        Long apartmentId,
        String communityName,
        SupportTicketRole raisedByRole,
        String raisedByName,
        String subject,
        boolean toAdmin,
        boolean toSystemAdmin,
        SupportTicketStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        String lastMessagePreview
) {
}
