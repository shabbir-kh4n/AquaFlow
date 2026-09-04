package com.shabbir.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.shabbir.backend.entity.Resident;

public record ResidentResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String houseNumber,
        String meterNumber,
        String governmentId,
        String gender,
        String dateOfBirth,
        String dateOfJoining,
        Instant createdAt,
        BigDecimal flatSizeSqft
) {
    public static ResidentResponse from(Resident resident) {
        return new ResidentResponse(
                resident.getId(),
                resident.getFullName(),
                resident.getEmail(),
                resident.getPhone(),
                resident.getHouseNumber(),
                resident.getMeterNumber(),
                resident.getGovernmentId(),
                resident.getGender(),
                resident.getDateOfBirth(),
                resident.getDateOfJoining(),
                resident.getCreatedAt(),
                resident.getHousehold() != null ? resident.getHousehold().getFlatSizeSqft() : null
        );
    }
}
