package com.shabbir.backend.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.shabbir.backend.dto.HouseholdResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.Household;
import com.shabbir.backend.repository.HouseholdRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HouseholdService {

    private final HouseholdRepository householdRepository;

    @Transactional
    public Household findOrCreate(Apartment apartment, String houseNumber, BigDecimal flatSizeSqft) {
        if (!StringUtils.hasText(houseNumber)) {
            return null;
        }

        return householdRepository.findByApartmentAndHouseNumber(apartment, houseNumber)
                .map(existing -> {
                    if (flatSizeSqft != null) {
                        existing.setFlatSizeSqft(flatSizeSqft);
                    }
                    return existing;
                })
                .orElseGet(() -> householdRepository.save(
                        Household.builder()
                                .apartment(apartment)
                                .houseNumber(houseNumber)
                                .flatSizeSqft(flatSizeSqft)
                                .build()
                ));
    }

    public List<HouseholdResponse> listForAdmin(Apartment apartment) {
        return householdRepository.findByApartmentOrderByHouseNumberAsc(apartment).stream()
                .map(HouseholdResponse::from)
                .toList();
    }
}
