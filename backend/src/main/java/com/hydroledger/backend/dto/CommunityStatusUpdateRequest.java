package com.hydroledger.backend.dto;

import com.hydroledger.backend.entity.CommunityAdminStatus;

import jakarta.validation.constraints.NotNull;

public record CommunityStatusUpdateRequest(
        @NotNull(message = "Status is required")
        CommunityAdminStatus status
) {
}
