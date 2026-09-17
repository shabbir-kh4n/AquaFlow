package com.hydroledger.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommonAreaConfig;

public interface CommonAreaConfigRepository extends JpaRepository<CommonAreaConfig, Long> {

    Optional<CommonAreaConfig> findByApartment(Apartment apartment);
}
