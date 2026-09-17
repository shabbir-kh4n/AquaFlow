package com.hydroledger.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.PasswordSetupToken;
import com.hydroledger.backend.entity.Resident;

public interface PasswordSetupTokenRepository extends JpaRepository<PasswordSetupToken, Long> {

    Optional<PasswordSetupToken> findByToken(String token);

    void deleteByResident(Resident resident);
}
