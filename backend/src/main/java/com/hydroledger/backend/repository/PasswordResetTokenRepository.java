package com.hydroledger.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.PasswordResetToken;
import com.hydroledger.backend.entity.PasswordResetToken.AccountType;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findByAccountTypeAndAccountIdAndUsedFalse(AccountType accountType, Long accountId);

    boolean existsByAccountTypeAndAccountIdAndCreatedAtAfter(AccountType accountType, Long accountId, Instant after);
}
