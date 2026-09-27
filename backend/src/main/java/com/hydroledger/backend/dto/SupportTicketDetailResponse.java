package com.hydroledger.backend.dto;

import java.time.Instant;
import java.util.List;

import com.hydroledger.backend.entity.SupportTicketRole;
import com.hydroledger.backend.entity.SupportTicketStatus;

public record SupportTicketDetailResponse(
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
        List<SupportTicketMessageResponse> messages
) {
}
