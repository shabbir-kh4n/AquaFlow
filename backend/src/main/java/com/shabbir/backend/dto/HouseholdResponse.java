package com.shabbir.backend.dto;

import java.time.Instant;

import com.shabbir.backend.entity.Household;

public record HouseholdResponse(
        Long id,
        String houseNumber,
        String floor,
        String notes,
        Instant createdAt
) {
    public static HouseholdResponse from(Household household) {
        return new HouseholdResponse(
                household.getId(),
                household.getHouseNumber(),
                household.getFloor(),
                household.getNotes(),
                household.getCreatedAt()
        );
    }
}
