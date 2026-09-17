package com.hydroledger.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;

/**
 * Regression test for the pre-existing bug where JwtAuthFilter only ever resolved the
 * principal against CommunityAdminRepository, meaning a Resident's valid JWT could never
 * authenticate a ROLE_RESIDENT-protected endpoint.
 */
class JwtAuthFilterResidentTests {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void residentJwtAuthenticatesAsRoleResident() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        CommunityAdminRepository communityAdminRepository = mock(CommunityAdminRepository.class);
        ResidentRepository residentRepository = mock(ResidentRepository.class);
        SystemAdminRepository systemAdminRepository = mock(SystemAdminRepository.class);

        Resident resident = Resident.builder()
                .id(1L)
                .fullName("Jane Resident")
                .email("resident@example.com")
                .password("hashed")
                .passwordSet(true)
                .build();

        String token = "a-resident-jwt";
        when(jwtService.extractUsername(token)).thenReturn("resident@example.com");
        when(communityAdminRepository.findByEmail("resident@example.com")).thenReturn(Optional.empty());
        when(residentRepository.findByEmail("resident@example.com")).thenReturn(Optional.of(resident));
        when(jwtService.isTokenValid(eq(token), any(UserDetails.class))).thenReturn(true);

        JwtAuthFilter filter = new JwtAuthFilter(jwtService, communityAdminRepository, residentRepository, systemAdminRepository);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(ResidentPrincipal.class);
        assertThat(((ResidentPrincipal) authentication.getPrincipal()).getUser().getEmail())
                .isEqualTo("resident@example.com");
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_RESIDENT");
    }

    @Test
    void adminJwtStillAuthenticatesAsRoleAdmin() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        CommunityAdminRepository communityAdminRepository = mock(CommunityAdminRepository.class);
        ResidentRepository residentRepository = mock(ResidentRepository.class);
        SystemAdminRepository systemAdminRepository = mock(SystemAdminRepository.class);

        CommunityAdmin admin = CommunityAdmin.builder()
                .id(2L)
                .fullName("Admin User")
                .email("admin@example.com")
                .password("hashed")
                .build();

        String token = "an-admin-jwt";
        when(jwtService.extractUsername(token)).thenReturn("admin@example.com");
        when(communityAdminRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(jwtService.isTokenValid(eq(token), any(UserDetails.class))).thenReturn(true);

        JwtAuthFilter filter = new JwtAuthFilter(jwtService, communityAdminRepository, residentRepository, systemAdminRepository);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(UserPrincipal.class);
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }
}
