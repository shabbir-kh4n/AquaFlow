package com.shabbir.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.TariffPlan;

public interface TariffPlanRepository extends JpaRepository<TariffPlan, Long> {

    Optional<TariffPlan> findByApartment(Apartment apartment);
}
