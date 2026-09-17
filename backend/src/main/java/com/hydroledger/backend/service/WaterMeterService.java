package com.hydroledger.backend.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.hydroledger.backend.dto.WaterMeterRequest;
import com.hydroledger.backend.dto.WaterMeterResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.MeterStatus;
import com.hydroledger.backend.entity.WaterMeter;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.WaterMeterRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WaterMeterService {

    private final WaterMeterRepository waterMeterRepository;

    @Transactional
    public WaterMeter findOrCreate(Apartment apartment, String meterNumber, Household household) {
        if (!StringUtils.hasText(meterNumber)) {
            return null;
        }

        WaterMeter meter = waterMeterRepository.findByApartmentAndMeterNumber(apartment, meterNumber)
                .orElseGet(() -> waterMeterRepository.save(
                        WaterMeter.builder()
                                .apartment(apartment)
                                .meterNumber(meterNumber)
                                .household(household)
                                .build()
                ));

        if (household != null && (meter.getHousehold() == null || !meter.getHousehold().getId().equals(household.getId()))) {
            meter.setHousehold(household);
        }

        return meter;
    }

    @Transactional
    public WaterMeterResponse create(WaterMeterRequest request, Apartment apartment) {
        if (waterMeterRepository.existsByApartmentAndMeterNumber(apartment, request.meterNumber())) {
            throw new ApiException(HttpStatus.CONFLICT, "A meter with this number already exists in your community");
        }

        WaterMeter meter = WaterMeter.builder()
                .apartment(apartment)
                .meterNumber(request.meterNumber())
                .installedAt(request.installedAt())
                .status(request.status() != null ? request.status() : MeterStatus.ACTIVE)
                .build();

        waterMeterRepository.save(meter);
        return WaterMeterResponse.from(meter);
    }

    @Transactional
    public WaterMeterResponse update(String meterNumber, WaterMeterRequest request, Apartment apartment) {
        WaterMeter meter = getEntity(meterNumber, apartment);

        if (!meter.getMeterNumber().equals(request.meterNumber())
                && waterMeterRepository.existsByApartmentAndMeterNumber(apartment, request.meterNumber())) {
            throw new ApiException(HttpStatus.CONFLICT, "A meter with this number already exists in your community");
        }

        meter.setMeterNumber(request.meterNumber());
        meter.setInstalledAt(request.installedAt());
        if (request.status() != null) {
            meter.setStatus(request.status());
        }

        return WaterMeterResponse.from(meter);
    }

    public WaterMeterResponse get(String meterNumber, Apartment apartment) {
        return WaterMeterResponse.from(getEntity(meterNumber, apartment));
    }

    public List<WaterMeterResponse> list(Apartment apartment) {
        return waterMeterRepository.findByApartmentOrderByMeterNumberAsc(apartment).stream()
                .map(WaterMeterResponse::from)
                .toList();
    }

    private WaterMeter getEntity(String meterNumber, Apartment apartment) {
        return waterMeterRepository.findByApartmentAndMeterNumber(apartment, meterNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Meter not found"));
    }
}
