package com.hydroledger.backend.dto;

import java.time.Instant;
import java.util.List;

import com.hydroledger.backend.entity.CommunityAdminStatus;

public record CommunityDetailResponse(
        Long adminId,
        String adminFullName,
        String adminEmail,
        String adminPhone,
        CommunityAdminStatus status,
        Long apartmentId,
        String apartmentName,
        String apartmentAddress,
        Instant adminCreatedAt,
        List<ResidentSummary> residents
) {
    public record ResidentSummary(
            Long id,
            String fullName,
            String email,
            String houseNumber,
            String meterNumber,
            boolean passwordSet
    ) {
    }
}
