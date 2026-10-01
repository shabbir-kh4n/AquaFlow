package com.hydroledger.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.PasswordResetToken;
import com.hydroledger.backend.entity.PasswordResetToken.AccountType;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.PasswordResetTokenRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;
import com.hydroledger.backend.service.EmailService;
import com.hydroledger.backend.service.PasswordResetService;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTests {

    @Mock
    private CommunityAdminRepository communityAdminRepository;

    @Mock
    private ResidentRepository residentRepository;

    @Mock
    private SystemAdminRepository systemAdminRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private PasswordResetService service;

    private final CommunityAdmin admin = CommunityAdmin.builder().id(5L).fullName("Ann").email("ann@x.com").password("old").build();

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void setUrl() {
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:5175");
    }

    @Test
    void unknownEmailSendsNothingAndSavesNothing() {
        when(communityAdminRepository.findByEmail("nobody@x.com")).thenReturn(Optional.empty());
        when(residentRepository.findByEmail("nobody@x.com")).thenReturn(Optional.empty());
        when(systemAdminRepository.findByEmail("nobody@x.com")).thenReturn(Optional.empty());

        service.requestReset("nobody@x.com");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    void knownAdminGetsHashedTokenStoredAndLinkEmailed() {
        setUrl();
        when(communityAdminRepository.findByEmail("ann@x.com")).thenReturn(Optional.of(admin));
        when(tokenRepository.existsByAccountTypeAndAccountIdAndCreatedAtAfter(eq(AccountType.ADMIN), eq(5L), any()))
                .thenReturn(false);

        service.requestReset("ann@x.com");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq("ann@x.com"), eq("Ann"), link.capture());

        String rawToken = link.getValue().substring("http://localhost:5175/reset-password/".length());
        assertThat(saved.getValue().getTokenHash()).hasSize(64).isNotEqualTo(rawToken);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(sha256(rawToken));
        assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void repeatRequestInsideCooldownIsIgnored() {
        when(communityAdminRepository.findByEmail("ann@x.com")).thenReturn(Optional.of(admin));
        when(tokenRepository.existsByAccountTypeAndAccountIdAndCreatedAtAfter(eq(AccountType.ADMIN), eq(5L), any()))
                .thenReturn(true);

        service.requestReset("ann@x.com");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    void residentFallbackIsUsedWhenNotAnAdmin() {
        setUrl();
        Resident resident = Resident.builder().id(9L).fullName("Raj").email("raj@x.com").build();
        when(communityAdminRepository.findByEmail("raj@x.com")).thenReturn(Optional.empty());
        when(residentRepository.findByEmail("raj@x.com")).thenReturn(Optional.of(resident));
        when(tokenRepository.existsByAccountTypeAndAccountIdAndCreatedAtAfter(eq(AccountType.RESIDENT), eq(9L), any()))
                .thenReturn(false);

        service.requestReset("raj@x.com");

        verify(emailService).sendPasswordResetEmail(eq("raj@x.com"), eq("Raj"), anyString());
    }

    @Test
    void resetWithValidTokenChangesPasswordAndBurnsOpenTokens() {
        PasswordResetToken token = PasswordResetToken.builder().accountType(AccountType.ADMIN).accountId(5L)
                .expiresAt(Instant.now().plusSeconds(600)).build();
        PasswordResetToken sibling = PasswordResetToken.builder().accountType(AccountType.ADMIN).accountId(5L)
                .expiresAt(Instant.now().plusSeconds(600)).build();
        when(tokenRepository.findByTokenHash(sha256("tok"))).thenReturn(Optional.of(token));
        when(communityAdminRepository.findById(5L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode("newpassword1")).thenReturn("ENC");
        when(tokenRepository.findByAccountTypeAndAccountIdAndUsedFalse(AccountType.ADMIN, 5L))
                .thenReturn(List.of(token, sibling));

        service.resetPassword("tok", "newpassword1");

        assertThat(admin.getPassword()).isEqualTo("ENC");
        assertThat(token.isUsed()).isTrue();
        assertThat(sibling.isUsed()).isTrue();
    }

    @Test
    void resetWithResidentTokenMarksPasswordSet() {
        Resident resident = Resident.builder().id(9L).fullName("Raj").email("raj@x.com").build();
        PasswordResetToken token = PasswordResetToken.builder().accountType(AccountType.RESIDENT).accountId(9L)
                .expiresAt(Instant.now().plusSeconds(600)).build();
        when(tokenRepository.findByTokenHash(sha256("tok"))).thenReturn(Optional.of(token));
        when(residentRepository.findById(9L)).thenReturn(Optional.of(resident));
        when(passwordEncoder.encode("newpassword1")).thenReturn("ENC");
        when(tokenRepository.findByAccountTypeAndAccountIdAndUsedFalse(AccountType.RESIDENT, 9L)).thenReturn(List.of(token));

        service.resetPassword("tok", "newpassword1");

        assertThat(resident.getPassword()).isEqualTo("ENC");
        assertThat(resident.isPasswordSet()).isTrue();
    }

    @Test
    void usedExpiredAndUnknownTokensAreRejected() {
        PasswordResetToken used = PasswordResetToken.builder().accountType(AccountType.ADMIN).accountId(5L)
                .expiresAt(Instant.now().plusSeconds(600)).used(true).build();
        PasswordResetToken expired = PasswordResetToken.builder().accountType(AccountType.ADMIN).accountId(5L)
                .expiresAt(Instant.now().minusSeconds(1)).build();
        when(tokenRepository.findByTokenHash(sha256("used"))).thenReturn(Optional.of(used));
        when(tokenRepository.findByTokenHash(sha256("expired"))).thenReturn(Optional.of(expired));
        when(tokenRepository.findByTokenHash(sha256("nope"))).thenReturn(Optional.empty());

        for (String t : new String[] {"used", "expired", "nope"}) {
            assertThatThrownBy(() -> service.resetPassword(t, "newpassword1")).isInstanceOf(ApiException.class);
        }
        verify(passwordEncoder, never()).encode(anyString());
    }
}
