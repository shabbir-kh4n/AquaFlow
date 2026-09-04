package com.shabbir.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shabbir.backend.entity.CommunityAdmin;

public interface CommunityAdminRepository extends JpaRepository<CommunityAdmin, Long> {

    Optional<CommunityAdmin> findByEmail(String email);

    boolean existsByEmail(String email);
}
