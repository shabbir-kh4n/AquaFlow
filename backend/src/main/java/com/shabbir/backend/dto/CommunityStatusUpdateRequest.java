package com.shabbir.backend.dto;

import com.shabbir.backend.entity.CommunityAdminStatus;

import jakarta.validation.constraints.NotNull;

public record CommunityStatusUpdateRequest(
        @NotNull(message = "Status is required")
        CommunityAdminStatus status
) {
}
