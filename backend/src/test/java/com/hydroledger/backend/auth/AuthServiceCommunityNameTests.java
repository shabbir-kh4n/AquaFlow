package com.hydroledger.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.hydroledger.backend.dto.AuthResponse;
import com.hydroledger.backend.dto.LoginRequest;
import com.hydroledger.backend.dto.RegisterRequest;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.Role;
import com.hydroledger.backend.entity.SystemAdmin;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.PasswordSetupTokenRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;
import com.hydroledger.backend.security.JwtService;
import com.hydroledger.backend.service.ApartmentService;
import com.hydroledger.backend.service.AuthService;

@ExtendWith(MockitoExtension.class)
class AuthServiceCommunityNameTests {

    @Mock
    private CommunityAdminRepository communityAdminRepository;

    @Mock
    private ResidentRepository residentRepository;

    @Mock
    private SystemAdminRepository systemAdminRepository;

    @Mock
    private PasswordSetupTokenRepository passwordSetupTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private ApartmentService apartmentService;

    @InjectMocks
    private AuthService authService;

    private CommunityAdmin admin;

    @BeforeEach
    void setUp() {
        admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").password("hashed").build();
    }

    @Test
    void registerCreatesApartmentWithGivenCommunityName() {
        RegisterRequest request = new RegisterRequest("Jane Admin", "jane@example.com", "password123",
                Role.ADMIN, null, null, "Sunrise Towers");
        when(communityAdminRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        Apartment apartment = Apartment.builder().id(1L).name("Sunrise Towers").admin(admin).build();
        when(apartmentService.getOrCreate(any(CommunityAdmin.class), org.mockito.ArgumentMatchers.eq("Sunrise Towers")))
                .thenReturn(apartment);
        when(jwtService.generateToken(any())).thenReturn("token");

        AuthResponse response = authService.register(request);

        assertThat(response.communityName()).isEqualTo("Sunrise Towers");
        assertThat(response.houseNumber()).isNull();
        assertThat(response.meterNumber()).isNull();
        assertThat(response.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void loginAdminBranchCarriesCommunityNameAndNullHouseMeter() {
        LoginRequest request = new LoginRequest("jane@example.com", "password123");
        when(communityAdminRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(admin));
        Apartment apartment = Apartment.builder().id(1L).name("Sunrise Towers").admin(admin).build();
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(jwtService.generateToken(any())).thenReturn("token");

        AuthResponse response = authService.login(request);

        assertThat(response.communityName()).isEqualTo("Sunrise Towers");
        assertThat(response.houseNumber()).isNull();
        assertThat(response.meterNumber()).isNull();
    }

    @Test
    void loginResidentBranchCarriesCommunityNameHouseAndMeter() {
        LoginRequest request = new LoginRequest("res@example.com", "password123");
        Resident resident = Resident.builder().id(2L).fullName("Res Ident").email("res@example.com")
                .houseNumber("A-101").meterNumber("MTR-0001").passwordSet(true).createdBy(admin).build();
        when(communityAdminRepository.findByEmail("res@example.com")).thenReturn(Optional.empty());
        when(residentRepository.findByEmailAndPasswordSetTrue("res@example.com")).thenReturn(Optional.of(resident));
        Apartment apartment = Apartment.builder().id(1L).name("Sunrise Towers").admin(admin).build();
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(jwtService.generateToken(any())).thenReturn("token");

        AuthResponse response = authService.login(request);

        assertThat(response.communityName()).isEqualTo("Sunrise Towers");
        assertThat(response.houseNumber()).isEqualTo("A-101");
        assertThat(response.meterNumber()).isEqualTo("MTR-0001");
        assertThat(response.role()).isEqualTo(Role.RESIDENT);
    }

    @Test
    void setPasswordCarriesCommunityNameHouseAndMeter() {
        Resident resident = Resident.builder().id(2L).fullName("Res Ident").email("res@example.com")
                .houseNumber("A-101").meterNumber("MTR-0001").createdBy(admin).build();
        com.hydroledger.backend.entity.PasswordSetupToken setupToken = com.hydroledger.backend.entity.PasswordSetupToken.builder()
                .id(1L).token("tok").resident(resident)
                .expiresAt(java.time.Instant.now().plusSeconds(3600)).used(false).build();
        when(passwordSetupTokenRepository.findByToken("tok")).thenReturn(Optional.of(setupToken));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        Apartment apartment = Apartment.builder().id(1L).name("Sunrise Towers").admin(admin).build();
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(jwtService.generateToken(any())).thenReturn("token");

        AuthResponse response = authService.setPassword(new com.hydroledger.backend.dto.SetPasswordRequest("tok", "newpassword"));

        assertThat(response.communityName()).isEqualTo("Sunrise Towers");
        assertThat(response.houseNumber()).isEqualTo("A-101");
        assertThat(response.meterNumber()).isEqualTo("MTR-0001");
    }

    @Test
    void loginSystemAdminBranchReturnsSystemAdminRole() {
        LoginRequest request = new LoginRequest("root@hydroledger.local", "password123");
        SystemAdmin sysAdmin = SystemAdmin.builder().id(9L).fullName("Root Admin").email("root@hydroledger.local")
                .password("hashed").build();
        when(communityAdminRepository.findByEmail("root@hydroledger.local")).thenReturn(Optional.empty());
        when(residentRepository.findByEmailAndPasswordSetTrue("root@hydroledger.local")).thenReturn(Optional.empty());
        when(systemAdminRepository.findByEmail("root@hydroledger.local")).thenReturn(Optional.of(sysAdmin));
        when(jwtService.generateToken(any())).thenReturn("token");

        AuthResponse response = authService.login(request);

        assertThat(response.role()).isEqualTo(Role.SYSTEM_ADMIN);
        assertThat(response.communityName()).isNull();
        assertThat(response.houseNumber()).isNull();
        assertThat(response.meterNumber()).isNull();
    }

    @Test
    void loginRejectsBlockedOrTerminatedAdminWithGenericUnauthorized() {
        LoginRequest request = new LoginRequest("jane@example.com", "password123");
        org.mockito.Mockito.doThrow(new DisabledException("disabled"))
                .when(authenticationManager).authenticate(any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid email or password");
    }
}
