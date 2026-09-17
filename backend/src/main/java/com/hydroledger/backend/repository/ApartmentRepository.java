package com.hydroledger.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;

public interface ApartmentRepository extends JpaRepository<Apartment, Long> {

    Optional<Apartment> findByAdmin(CommunityAdmin admin);
}
