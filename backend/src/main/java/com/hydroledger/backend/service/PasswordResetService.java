package com.hydroledger.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.PasswordResetToken;
import com.hydroledger.backend.entity.PasswordResetToken.AccountType;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.SystemAdmin;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.PasswordResetTokenRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;

import lombok.RequiredArgsConstructor;

/**
 * "Forgot password" for every account type: a one-hour, single-use link is emailed to the account's
 * address. Requesting a reset never reveals whether the email is registered.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofHours(1);
    // Ignore repeat requests for the same account inside this window so the endpoint can't be used to spam a mailbox.
    static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CommunityAdminRepository communityAdminRepository;
    private final ResidentRepository residentRepository;
    private final SystemAdminRepository systemAdminRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private record Account(AccountType type, Long id, String name, String email) {
    }

    @Transactional
    public void requestReset(String email) {
        Optional<Account> found = findAccount(email.trim());
        if (found.isEmpty()) {
            return;
        }
        Account account = found.get();
        if (tokenRepository.existsByAccountTypeAndAccountIdAndCreatedAtAfter(
                account.type(), account.id(), Instant.now().minus(REQUEST_COOLDOWN))) {
            return;
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        tokenRepository.save(PasswordResetToken.builder()
                .tokenHash(hash(token))
                .accountType(account.type())
                .accountId(account.id())
                .expiresAt(Instant.now().plus(TOKEN_TTL))
                .build());

        emailService.sendPasswordResetEmail(account.email(), account.name(), frontendUrl + "/reset-password/" + token);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken record = tokenRepository.findByTokenHash(hash(token))
                .filter(t -> !t.isUsed() && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This reset link is invalid or has expired"));

        String encoded = passwordEncoder.encode(newPassword);
        switch (record.getAccountType()) {
            case ADMIN -> {
                CommunityAdmin admin = communityAdminRepository.findById(record.getAccountId())
                        .orElseThrow(PasswordResetService::linkInvalid);
                admin.setPassword(encoded);
                communityAdminRepository.save(admin);
            }
            case RESIDENT -> {
                Resident resident = residentRepository.findById(record.getAccountId())
                        .orElseThrow(PasswordResetService::linkInvalid);
                resident.setPassword(encoded);
                resident.setPasswordSet(true);
                residentRepository.save(resident);
            }
            case SYSTEM_ADMIN -> {
                SystemAdmin systemAdmin = systemAdminRepository.findById(record.getAccountId())
                        .orElseThrow(PasswordResetService::linkInvalid);
                systemAdmin.setPassword(encoded);
                systemAdminRepository.save(systemAdmin);
            }
        }

        // Single use: burn this token and any other still-open reset links for the same account.
        tokenRepository.findByAccountTypeAndAccountIdAndUsedFalse(record.getAccountType(), record.getAccountId())
                .forEach(t -> t.setUsed(true));
    }

    private Optional<Account> findAccount(String email) {
        Optional<CommunityAdmin> admin = communityAdminRepository.findByEmail(email);
        if (admin.isPresent()) {
            CommunityAdmin a = admin.get();
            return Optional.of(new Account(AccountType.ADMIN, a.getId(), a.getFullName(), a.getEmail()));
        }
        Optional<Resident> resident = residentRepository.findByEmail(email);
        if (resident.isPresent()) {
            Resident r = resident.get();
            return Optional.of(new Account(AccountType.RESIDENT, r.getId(), r.getFullName(), r.getEmail()));
        }
        return systemAdminRepository.findByEmail(email)
                .map(s -> new Account(AccountType.SYSTEM_ADMIN, s.getId(), s.getFullName(), s.getEmail()));
    }

    private static ApiException linkInvalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "This reset link is invalid or has expired");
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
