package com.hydroledger.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.WaterMeter;

public interface WaterMeterRepository extends JpaRepository<WaterMeter, Long> {

    Optional<WaterMeter> findByApartmentAndMeterNumber(Apartment apartment, String meterNumber);

    List<WaterMeter> findByApartmentOrderByMeterNumberAsc(Apartment apartment);

    boolean existsByApartmentAndMeterNumber(Apartment apartment, String meterNumber);

    Optional<WaterMeter> findByHousehold(Household household);
}
