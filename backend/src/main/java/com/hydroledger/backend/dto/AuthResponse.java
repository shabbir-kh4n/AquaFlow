package com.hydroledger.backend.dto;

import com.hydroledger.backend.entity.Role;

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
