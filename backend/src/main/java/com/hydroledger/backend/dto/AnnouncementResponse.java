package com.hydroledger.backend.dto;

import java.time.Instant;

import com.hydroledger.backend.entity.AnnouncementCategory;

public record AnnouncementResponse(
        Long id,
        String title,
        String body,
        AnnouncementCategory category,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        boolean expired
) {
}
