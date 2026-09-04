package com.shabbir.backend.dto;

import com.shabbir.backend.entity.Role;

public record AuthResponse(
        String token,
        Long id,
        String fullName,
        String email,
        Role role,
        String communityName,
        String houseNumber,
        String meterNumber
) {
}
