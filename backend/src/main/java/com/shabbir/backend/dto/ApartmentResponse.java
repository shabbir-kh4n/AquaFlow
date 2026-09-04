package com.shabbir.backend.dto;

import java.time.Instant;

import com.shabbir.backend.entity.Apartment;

public record ApartmentResponse(
        Long id,
        String name,
        String address,
        String mainsSource,
        Instant createdAt
) {
    public static ApartmentResponse from(Apartment apartment) {
        return new ApartmentResponse(
                apartment.getId(),
                apartment.getName(),
                apartment.getAddress(),
                apartment.getMainsSource(),
                apartment.getCreatedAt()
        );
    }
}
