package com.hydroledger.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.Alert;
import com.hydroledger.backend.entity.AlertType;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.Household;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByApartmentOrderByCreatedAtDesc(Apartment apartment);

    List<Alert> findByHouseholdOrderByCreatedAtDesc(Household household);

    Optional<Alert> findByHouseholdAndPeriodMonthAndAlertType(Household household, String periodMonth, AlertType alertType);

    Optional<Alert> findByIdAndApartment(Long id, Apartment apartment);
}
