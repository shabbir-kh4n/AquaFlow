package com.shabbir.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.BillingCycle;

public interface BillingCycleRepository extends JpaRepository<BillingCycle, Long> {

    List<BillingCycle> findByApartmentOrderByPeriodMonthDesc(Apartment apartment);

    Optional<BillingCycle> findByIdAndApartment(Long id, Apartment apartment);

    boolean existsByApartmentAndPeriodMonth(Apartment apartment, String periodMonth);
}
