package com.shabbir.backend.meter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shabbir.backend.dto.WaterMeterRequest;
import com.shabbir.backend.dto.WaterMeterResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.Household;
import com.shabbir.backend.entity.MeterStatus;
import com.shabbir.backend.entity.WaterMeter;
import com.shabbir.backend.exception.ApiException;
import com.shabbir.backend.repository.WaterMeterRepository;
import com.shabbir.backend.service.WaterMeterService;

@ExtendWith(MockitoExtension.class)
class WaterMeterServiceTests {

    @Mock
    private WaterMeterRepository waterMeterRepository;

    @InjectMocks
    private WaterMeterService waterMeterService;

    private final Apartment apartment = Apartment.builder().id(1L).name("Sunrise Towers").build();
    private final Apartment otherApartment = Apartment.builder().id(2L).name("Other Towers").build();

    @Test
    void createSavesNewMeter() {
        when(waterMeterRepository.existsByApartmentAndMeterNumber(apartment, "MTR-0001")).thenReturn(false);
        when(waterMeterRepository.save(any(WaterMeter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WaterMeterResponse response = waterMeterService.create(
                new WaterMeterRequest("MTR-0001", null, null), apartment);

        assertThat(response.meterNumber()).isEqualTo("MTR-0001");
        assertThat(response.status()).isEqualTo(MeterStatus.ACTIVE);
    }

    @Test
    void createThrowsConflictOnDuplicateMeterNumber() {
        when(waterMeterRepository.existsByApartmentAndMeterNumber(apartment, "MTR-0001")).thenReturn(true);

        assertThatThrownBy(() -> waterMeterService.create(new WaterMeterRequest("MTR-0001", null, null), apartment))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");

        verify(waterMeterRepository, never()).save(any());
    }

    @Test
    void updateModifiesExistingMeter() {
        WaterMeter existing = WaterMeter.builder().id(1L).apartment(apartment).meterNumber("MTR-0001")
                .status(MeterStatus.ACTIVE).build();
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-0001")).thenReturn(Optional.of(existing));

        WaterMeterResponse response = waterMeterService.update(
                "MTR-0001", new WaterMeterRequest("MTR-0001", null, MeterStatus.INACTIVE), apartment);

        assertThat(response.status()).isEqualTo(MeterStatus.INACTIVE);
    }

    @Test
    void updateThrowsNotFoundForUnknownMeter() {
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> waterMeterService.update(
                "MTR-9999", new WaterMeterRequest("MTR-9999", null, null), apartment))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getIsScopedToApartmentTenant() {
        when(waterMeterRepository.findByApartmentAndMeterNumber(otherApartment, "MTR-0001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> waterMeterService.get("MTR-0001", otherApartment))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void listReturnsMetersForApartment() {
        WaterMeter meter = WaterMeter.builder().id(1L).apartment(apartment).meterNumber("MTR-0001")
                .status(MeterStatus.ACTIVE).build();
        when(waterMeterRepository.findByApartmentOrderByMeterNumberAsc(apartment)).thenReturn(List.of(meter));

        List<WaterMeterResponse> responses = waterMeterService.list(apartment);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).meterNumber()).isEqualTo("MTR-0001");
    }

    @Test
    void findOrCreateReturnsNullForBlankMeterNumber() {
        assertThat(waterMeterService.findOrCreate(apartment, "", null)).isNull();
        assertThat(waterMeterService.findOrCreate(apartment, null, null)).isNull();
    }

    @Test
    void findOrCreateRepointsMeterWhenHouseholdChanges() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        Household otherHousehold = Household.builder().id(2L).apartment(apartment).houseNumber("B-202").build();
        WaterMeter existing = WaterMeter.builder().id(1L).apartment(apartment).meterNumber("MTR-0001")
                .household(household).build();
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-0001")).thenReturn(Optional.of(existing));

        WaterMeter result = waterMeterService.findOrCreate(apartment, "MTR-0001", otherHousehold);

        // A resident's house number can be edited to a value that resolves to a different Household row
        // (e.g. "03" vs "3") — the meter must follow the resident to the new household, not stay pinned
        // to the old one, otherwise the old household keeps stealing the resident's real consumption.
        assertThat(result.getHousehold()).isEqualTo(otherHousehold);
    }

    @Test
    void findOrCreateLeavesMeterUnchangedWhenHouseholdAlreadyMatches() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        WaterMeter existing = WaterMeter.builder().id(1L).apartment(apartment).meterNumber("MTR-0001")
                .household(household).build();
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-0001")).thenReturn(Optional.of(existing));

        WaterMeter result = waterMeterService.findOrCreate(apartment, "MTR-0001", household);

        assertThat(result.getHousehold()).isEqualTo(household);
    }

    @Test
    void findOrCreateCreatesMeterLinkedToHouseholdWhenAbsent() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-0001")).thenReturn(Optional.empty());
        when(waterMeterRepository.save(any(WaterMeter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WaterMeter result = waterMeterService.findOrCreate(apartment, "MTR-0001", household);

        assertThat(result.getHousehold()).isEqualTo(household);
        assertThat(result.getMeterNumber()).isEqualTo("MTR-0001");
    }
}
