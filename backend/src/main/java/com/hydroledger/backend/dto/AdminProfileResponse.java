package com.hydroledger.backend.dto;

import com.hydroledger.backend.entity.CommunityAdmin;

public record AdminProfileResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String avatarKey
) {
    public static AdminProfileResponse from(CommunityAdmin admin) {
        return new AdminProfileResponse(
                admin.getId(),
                admin.getFullName(),
                admin.getEmail(),
                admin.getPhone(),
                admin.getAvatarKey()
        );
    }
}
