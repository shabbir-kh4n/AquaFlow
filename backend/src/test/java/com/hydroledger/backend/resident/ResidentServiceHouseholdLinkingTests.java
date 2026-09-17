package com.hydroledger.backend.resident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.hydroledger.backend.dto.ResidentRequest;
import com.hydroledger.backend.dto.ResidentResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.PasswordSetupTokenRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.service.ApartmentService;
import com.hydroledger.backend.service.EmailService;
import com.hydroledger.backend.service.HouseholdService;
import com.hydroledger.backend.service.ResidentService;
import com.hydroledger.backend.service.WaterMeterService;

@ExtendWith(MockitoExtension.class)
class ResidentServiceHouseholdLinkingTests {

    @Mock
    private ResidentRepository residentRepository;

    @Mock
    private PasswordSetupTokenRepository passwordSetupTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private ApartmentService apartmentService;

    @Mock
    private HouseholdService householdService;

    @Mock
    private WaterMeterService waterMeterService;

    @InjectMocks
    private ResidentService residentService;

    private CommunityAdmin admin;
    private Apartment apartment;
    private Household household;
    private ResidentRequest request;

    @BeforeEach
    void setUp() {
        admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();
        apartment = Apartment.builder().id(1L).name("Sunrise Towers").admin(admin).build();
        household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        request = new ResidentRequest("Res Ident", "res@example.com", "1234567890", "A-101", "MTR-0001",
                "998765", "Other", "2000-01-01", "2026-01-01", true, "password123", null);
    }

    @Test
    void createTransparentlyLinksHouseholdAndMeter() {
        when(residentRepository.existsByEmailAndCreatedBy(request.email(), admin)).thenReturn(false);
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(householdService.findOrCreate(apartment, "A-101", null)).thenReturn(household);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(residentRepository.save(any(Resident.class))).thenAnswer(inv -> inv.getArgument(0));

        ResidentResponse response = residentService.create(request, admin);

        assertThat(response.houseNumber()).isEqualTo("A-101");
        assertThat(response.meterNumber()).isEqualTo("MTR-0001");
        verify(waterMeterService).findOrCreate(apartment, "MTR-0001", household);
    }

    @Test
    void updateTransparentlyRelinksHouseholdAndMeter() {
        Resident existing = Resident.builder().id(5L).fullName("Old Name").email("old@example.com")
                .houseNumber("Z-999").meterNumber("MTR-OLD").createdBy(admin).build();
        when(residentRepository.findByIdAndCreatedBy(5L, admin)).thenReturn(Optional.of(existing));
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(householdService.findOrCreate(apartment, "A-101", null)).thenReturn(household);

        ResidentResponse response = residentService.update(5L, request, admin);

        assertThat(response.houseNumber()).isEqualTo("A-101");
        assertThat(existing.getHousehold()).isEqualTo(household);
        verify(waterMeterService).findOrCreate(apartment, "MTR-0001", household);
    }

    @Test
    void createRejectsDuplicateHouseNumberForSameAdmin() {
        when(residentRepository.existsByEmailAndCreatedBy(request.email(), admin)).thenReturn(false);
        when(residentRepository.existsByHouseNumberAndCreatedBy("A-101", admin)).thenReturn(true);

        assertThatThrownBy(() -> residentService.create(request, admin))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("house number")
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(residentRepository, never()).save(any(Resident.class));
    }

    @Test
    void createRejectsDuplicateMeterNumberForSameAdmin() {
        when(residentRepository.existsByEmailAndCreatedBy(request.email(), admin)).thenReturn(false);
        when(residentRepository.existsByHouseNumberAndCreatedBy("A-101", admin)).thenReturn(false);
        when(residentRepository.existsByMeterNumberAndCreatedBy("MTR-0001", admin)).thenReturn(true);

        assertThatThrownBy(() -> residentService.create(request, admin))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("meter number")
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(residentRepository, never()).save(any(Resident.class));
    }

    @Test
    void updateRejectsHouseNumberAlreadyAssignedToAnotherResident() {
        Resident existing = Resident.builder().id(5L).fullName("Old Name").email("old@example.com")
                .houseNumber("Z-999").meterNumber("MTR-OLD").createdBy(admin).build();
        when(residentRepository.findByIdAndCreatedBy(5L, admin)).thenReturn(Optional.of(existing));
        when(residentRepository.existsByHouseNumberAndCreatedByAndIdNot("A-101", admin, 5L)).thenReturn(true);

        assertThatThrownBy(() -> residentService.update(5L, request, admin))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("house number")
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateAllowsKeepingResidentsOwnHouseAndMeterNumber() {
        Resident existing = Resident.builder().id(5L).fullName("Old Name").email("old@example.com")
                .houseNumber("A-101").meterNumber("MTR-0001").createdBy(admin).build();
        when(residentRepository.findByIdAndCreatedBy(5L, admin)).thenReturn(Optional.of(existing));
        when(residentRepository.existsByHouseNumberAndCreatedByAndIdNot("A-101", admin, 5L)).thenReturn(false);
        when(residentRepository.existsByMeterNumberAndCreatedByAndIdNot("MTR-0001", admin, 5L)).thenReturn(false);
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(householdService.findOrCreate(apartment, "A-101", null)).thenReturn(household);

        ResidentResponse response = residentService.update(5L, request, admin);

        assertThat(response.houseNumber()).isEqualTo("A-101");
    }

    @Test
    void residentResponseShapeUnchanged() {
        when(residentRepository.existsByEmailAndCreatedBy(request.email(), admin)).thenReturn(false);
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(householdService.findOrCreate(apartment, "A-101", null)).thenReturn(household);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(residentRepository.save(any(Resident.class))).thenAnswer(inv -> inv.getArgument(0));

        ResidentResponse response = residentService.create(request, admin);

        assertThat(response.fullName()).isEqualTo("Res Ident");
        assertThat(response.email()).isEqualTo("res@example.com");
        assertThat(response.phone()).isEqualTo("1234567890");
        assertThat(response.governmentId()).isEqualTo("998765");
        assertThat(response.gender()).isEqualTo("Other");
        assertThat(response.dateOfBirth()).isEqualTo("2000-01-01");
        assertThat(response.dateOfJoining()).isEqualTo("2026-01-01");
    }
}
