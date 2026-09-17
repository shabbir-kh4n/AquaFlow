package com.hydroledger.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Resident;

public interface ResidentRepository extends JpaRepository<Resident, Long> {

    List<Resident> findByCreatedByOrderByCreatedAtDesc(CommunityAdmin createdBy);

    Optional<Resident> findByHousehold(Household household);

    Optional<Resident> findByIdAndCreatedBy(Long id, CommunityAdmin createdBy);

    boolean existsByEmailAndCreatedBy(String email, CommunityAdmin createdBy);

    boolean existsByHouseNumberAndCreatedBy(String houseNumber, CommunityAdmin createdBy);

    boolean existsByHouseNumberAndCreatedByAndIdNot(String houseNumber, CommunityAdmin createdBy, Long id);

    boolean existsByMeterNumberAndCreatedBy(String meterNumber, CommunityAdmin createdBy);

    boolean existsByMeterNumberAndCreatedByAndIdNot(String meterNumber, CommunityAdmin createdBy, Long id);

    Optional<Resident> findByEmailAndPasswordSetTrue(String email);

    Optional<Resident> findByEmail(String email);
}
