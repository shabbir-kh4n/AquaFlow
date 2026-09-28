package com.hydroledger.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.Announcement;
import com.hydroledger.backend.entity.Apartment;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findByApartmentOrderByCreatedAtDesc(Apartment apartment);

    Optional<Announcement> findByIdAndApartment(Long id, Apartment apartment);
}
