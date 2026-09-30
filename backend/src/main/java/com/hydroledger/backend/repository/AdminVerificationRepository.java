package com.hydroledger.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.AdminVerification;
import com.hydroledger.backend.entity.CommunityAdmin;

public interface AdminVerificationRepository extends JpaRepository<AdminVerification, Long> {

    Optional<AdminVerification> findByAdmin(CommunityAdmin admin);

    void deleteByAdmin(CommunityAdmin admin);
}
