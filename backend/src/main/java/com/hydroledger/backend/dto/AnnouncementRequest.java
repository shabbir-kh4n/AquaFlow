package com.hydroledger.backend.dto;

import java.time.Instant;

import com.hydroledger.backend.entity.AnnouncementCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AnnouncementRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @NotBlank(message = "Message is required")
        @Size(max = 5000, message = "Message must be at most 5000 characters")
        String body,

        @NotNull(message = "Category is required")
        AnnouncementCategory category,

        // Boxed so an omitted field reads as "not pinned" rather than failing deserialization.
        Boolean pinned,

        // Optional - null means the announcement never expires.
        Instant expiresAt
) {
    public boolean isPinned() {
        return Boolean.TRUE.equals(pinned);
    }
}
