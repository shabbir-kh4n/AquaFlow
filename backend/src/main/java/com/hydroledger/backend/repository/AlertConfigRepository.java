package com.hydroledger.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.AlertConfig;
import com.hydroledger.backend.entity.Apartment;

public interface AlertConfigRepository extends JpaRepository<AlertConfig, Long> {

    Optional<AlertConfig> findByApartment(Apartment apartment);
}
