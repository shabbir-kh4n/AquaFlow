package com.hydroledger.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.CommunityAdminStatus;
import com.hydroledger.backend.entity.SystemAdmin;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;

@ExtendWith(MockitoExtension.class)
class SystemAdminAuthTests {

    @Mock
    private CommunityAdminRepository communityAdminRepository;

    @Mock
    private ResidentRepository residentRepository;

    @Mock
    private SystemAdminRepository systemAdminRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void resolvesSystemAdminPrincipalWhenNoCommunityAdminOrResidentMatches() {
        SystemAdmin systemAdmin = SystemAdmin.builder().id(1L).fullName("Root").email("root@hydroledger.local")
                .password("hashed").build();

        when(communityAdminRepository.findByEmail("root@hydroledger.local")).thenReturn(Optional.empty());
        when(residentRepository.findByEmailAndPasswordSetTrue("root@hydroledger.local")).thenReturn(Optional.empty());
        when(systemAdminRepository.findByEmail("root@hydroledger.local")).thenReturn(Optional.of(systemAdmin));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("root@hydroledger.local");

        assertThat(userDetails).isInstanceOf(SystemAdminPrincipal.class);
        assertThat(userDetails.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_SYSTEM_ADMIN");
    }

    @Test
    void activeAdminIsEnabledAndNonLocked() {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).email("admin@example.com").password("hashed")
                .status(CommunityAdminStatus.ACTIVE).build();

        UserPrincipal principal = new UserPrincipal(admin);

        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
    }

    @Test
    void blockedAdminIsDisabledAndLocked() {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).email("admin@example.com").password("hashed")
                .status(CommunityAdminStatus.BLOCKED).build();

        UserPrincipal principal = new UserPrincipal(admin);

        assertThat(principal.isEnabled()).isFalse();
        assertThat(principal.isAccountNonLocked()).isFalse();
    }

    @Test
    void terminatedAdminIsDisabledButNotFlaggedAsLocked() {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).email("admin@example.com").password("hashed")
                .status(CommunityAdminStatus.TERMINATED).build();

        UserPrincipal principal = new UserPrincipal(admin);

        assertThat(principal.isEnabled()).isFalse();
        assertThat(principal.isAccountNonLocked()).isTrue();
    }
}
