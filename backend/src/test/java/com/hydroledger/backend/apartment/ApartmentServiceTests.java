package com.hydroledger.backend.apartment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hydroledger.backend.dto.ApartmentRequest;
import com.hydroledger.backend.dto.ApartmentResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.repository.ApartmentRepository;
import com.hydroledger.backend.service.ApartmentService;

@ExtendWith(MockitoExtension.class)
class ApartmentServiceTests {

    @Mock
    private ApartmentRepository apartmentRepository;

    @InjectMocks
    private ApartmentService apartmentService;

    private CommunityAdmin admin;

    @BeforeEach
    void setUp() {
        admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();
    }

    @Test
    void getOrCreateReturnsExistingApartmentWhenPresent() {
        Apartment existing = Apartment.builder().id(10L).name("Existing Apartment").admin(admin).build();
        when(apartmentRepository.findByAdmin(admin)).thenReturn(Optional.of(existing));

        Apartment result = apartmentService.getOrCreate(admin);

        assertThat(result).isSameAs(existing);
        verify(apartmentRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void getOrCreateCreatesDefaultNamedApartmentWhenAbsent() {
        when(apartmentRepository.findByAdmin(admin)).thenReturn(Optional.empty());
        when(apartmentRepository.save(any(Apartment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Apartment result = apartmentService.getOrCreate(admin);

        assertThat(result.getName()).isEqualTo("Jane Admin's Apartment");
        assertThat(result.getAdmin()).isEqualTo(admin);
    }

    @Test
    void getOrCreateWithExplicitNameUsesThatNameOnFirstCreation() {
        when(apartmentRepository.findByAdmin(admin)).thenReturn(Optional.empty());
        when(apartmentRepository.save(any(Apartment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Apartment result = apartmentService.getOrCreate(admin, "Sunrise Towers");

        assertThat(result.getName()).isEqualTo("Sunrise Towers");
    }

    @Test
    void updateRenamesExistingApartment() {
        Apartment existing = Apartment.builder().id(10L).name("Old Name").admin(admin).build();
        when(apartmentRepository.findByAdmin(admin)).thenReturn(Optional.of(existing));

        ApartmentResponse response = apartmentService.update(new ApartmentRequest("New Name", "123 Main St", "Municipal Supply"), admin);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.address()).isEqualTo("123 Main St");
        assertThat(response.mainsSource()).isEqualTo("Municipal Supply");
        assertThat(existing.getName()).isEqualTo("New Name");
    }

    @Test
    void getForAdminIsIdempotent() {
        when(apartmentRepository.findByAdmin(admin)).thenReturn(Optional.empty());
        when(apartmentRepository.save(any(Apartment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApartmentResponse response = apartmentService.getForAdmin(admin);

        assertThat(response.name()).isEqualTo("Jane Admin's Apartment");
    }
}
