package com.shabbir.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shabbir.backend.entity.Alert;
import com.shabbir.backend.entity.AlertType;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.Household;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByApartmentOrderByCreatedAtDesc(Apartment apartment);

    List<Alert> findByHouseholdOrderByCreatedAtDesc(Household household);

    Optional<Alert> findByHouseholdAndPeriodMonthAndAlertType(Household household, String periodMonth, AlertType alertType);

    Optional<Alert> findByIdAndApartment(Long id, Apartment apartment);
}
