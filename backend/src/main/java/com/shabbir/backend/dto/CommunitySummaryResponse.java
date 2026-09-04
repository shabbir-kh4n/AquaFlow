package com.shabbir.backend.dto;

import java.time.Instant;

import com.shabbir.backend.entity.CommunityAdminStatus;

public record CommunitySummaryResponse(
        Long adminId,
        String adminFullName,
        String adminEmail,
        CommunityAdminStatus status,
        Long apartmentId,
        String apartmentName,
        int householdCount,
        int residentCount,
        Instant adminCreatedAt
) {
}
