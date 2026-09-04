package com.shabbir.backend.dto;

import com.shabbir.backend.entity.CommunityAdmin;

public record AdminProfileResponse(
        Long id,
        String fullName,
        String email,
        String phone
) {
    public static AdminProfileResponse from(CommunityAdmin admin) {
        return new AdminProfileResponse(
                admin.getId(),
                admin.getFullName(),
                admin.getEmail(),
                admin.getPhone()
        );
    }
}
