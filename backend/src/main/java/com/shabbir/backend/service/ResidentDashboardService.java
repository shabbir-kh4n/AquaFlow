package com.shabbir.backend.service;

import org.springframework.stereotype.Service;

import com.shabbir.backend.dto.ResidentDashboardResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.Resident;
import com.shabbir.backend.entity.WaterUsageReading;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResidentDashboardService {

    private final ApartmentService apartmentService;
    private final WaterUsageReadingService waterUsageReadingService;

    public ResidentDashboardResponse getDashboard(Resident resident) {
        Apartment apartment = apartmentService.getOrCreate(resident.getCreatedBy());
        WaterUsageReading latest = waterUsageReadingService.getLatestForResident(resident).orElse(null);

        return new ResidentDashboardResponse(
                apartment.getName(),
                resident.getHouseNumber(),
                resident.getMeterNumber(),
                latest != null ? latest.getReadingValue() : null,
                latest != null ? latest.getReadingDate() : null
        );
    }
}
