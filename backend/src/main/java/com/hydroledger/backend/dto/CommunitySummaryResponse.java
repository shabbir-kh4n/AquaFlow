package com.hydroledger.backend.dto;

import java.time.Instant;

import com.hydroledger.backend.entity.CommunityAdminStatus;
import com.hydroledger.backend.entity.VerificationStatus;

public record CommunitySummaryResponse(
        Long adminId,
        String adminFullName,
        String adminEmail,
        CommunityAdminStatus status,
        Long apartmentId,
        String apartmentName,
        int householdCount,
        int residentCount,
        Instant adminCreatedAt,
        String avatarKey,
        VerificationStatus verificationStatus
) {
}
