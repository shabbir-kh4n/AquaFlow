package com.shabbir.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.CommonAreaConfig;

public interface CommonAreaConfigRepository extends JpaRepository<CommonAreaConfig, Long> {

    Optional<CommonAreaConfig> findByApartment(Apartment apartment);
}
