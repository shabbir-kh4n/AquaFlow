package com.hydroledger.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.TariffPlan;

public interface TariffPlanRepository extends JpaRepository<TariffPlan, Long> {

    Optional<TariffPlan> findByApartment(Apartment apartment);
}
