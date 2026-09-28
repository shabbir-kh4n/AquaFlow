package com.hydroledger.backend.dto;

import com.hydroledger.backend.entity.SystemAdmin;

public record PlatformProfileResponse(
        Long id,
        String fullName,
        String email,
        String avatarKey
) {
    public static PlatformProfileResponse from(SystemAdmin systemAdmin) {
        return new PlatformProfileResponse(
                systemAdmin.getId(),
                systemAdmin.getFullName(),
                systemAdmin.getEmail(),
                systemAdmin.getAvatarKey()
        );
    }
}
