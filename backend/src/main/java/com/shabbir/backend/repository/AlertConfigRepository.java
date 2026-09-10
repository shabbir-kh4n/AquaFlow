package com.shabbir.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shabbir.backend.entity.AlertConfig;
import com.shabbir.backend.entity.Apartment;

public interface AlertConfigRepository extends JpaRepository<AlertConfig, Long> {

    Optional<AlertConfig> findByApartment(Apartment apartment);
}
