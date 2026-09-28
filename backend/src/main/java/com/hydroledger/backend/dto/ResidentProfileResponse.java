package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ResidentProfileResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String houseNumber,
        String meterNumber,
        String communityName,
        BigDecimal flatSizeSqft,
        String dateOfJoining,
        Instant createdAt,
        String avatarKey
) {
}
