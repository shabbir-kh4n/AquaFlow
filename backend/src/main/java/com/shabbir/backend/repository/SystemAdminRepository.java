package com.shabbir.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shabbir.backend.entity.SystemAdmin;

public interface SystemAdminRepository extends JpaRepository<SystemAdmin, Long> {

    Optional<SystemAdmin> findByEmail(String email);
}
