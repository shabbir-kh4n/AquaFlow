package com.hydroledger.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.ResidentRecommendation;

public interface ResidentRecommendationRepository extends JpaRepository<ResidentRecommendation, Long> {

    Optional<ResidentRecommendation> findByResident(Resident resident);
}
