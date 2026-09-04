package com.shabbir.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.CommunityAdmin;

public interface ApartmentRepository extends JpaRepository<Apartment, Long> {

    Optional<Apartment> findByAdmin(CommunityAdmin admin);
}
