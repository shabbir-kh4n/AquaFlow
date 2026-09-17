package com.hydroledger.backend.household;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hydroledger.backend.dto.HouseholdResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.service.HouseholdService;

@ExtendWith(MockitoExtension.class)
class HouseholdServiceTests {

    @Mock
    private HouseholdRepository householdRepository;

    @InjectMocks
    private HouseholdService householdService;

    private final Apartment apartment = Apartment.builder().id(1L).name("Sunrise Towers").build();

    @Test
    void findOrCreateReturnsNullForBlankHouseNumber() {
        assertThat(householdService.findOrCreate(apartment, "  ", null)).isNull();
        assertThat(householdService.findOrCreate(apartment, null, null)).isNull();
    }

    @Test
    void findOrCreateReturnsExistingHousehold() {
        Household existing = Household.builder().id(5L).apartment(apartment).houseNumber("A-101").build();
        when(householdRepository.findByApartmentAndHouseNumber(apartment, "A-101")).thenReturn(Optional.of(existing));

        Household result = householdService.findOrCreate(apartment, "A-101", null);

        assertThat(result).isSameAs(existing);
        verify(householdRepository, never()).save(any());
    }

    @Test
    void findOrCreateCreatesNewHouseholdWhenAbsent() {
        when(householdRepository.findByApartmentAndHouseNumber(apartment, "B-202")).thenReturn(Optional.empty());
        when(householdRepository.save(any(Household.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Household result = householdService.findOrCreate(apartment, "B-202", null);

        assertThat(result.getHouseNumber()).isEqualTo("B-202");
        assertThat(result.getApartment()).isEqualTo(apartment);
    }

    @Test
    void findOrCreateSetsFlatSizeSqftOnNewHousehold() {
        when(householdRepository.findByApartmentAndHouseNumber(apartment, "B-202")).thenReturn(Optional.empty());
        when(householdRepository.save(any(Household.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Household result = householdService.findOrCreate(apartment, "B-202", new BigDecimal("850.50"));

        assertThat(result.getFlatSizeSqft()).isEqualByComparingTo("850.50");
    }

    @Test
    void findOrCreateUpdatesFlatSizeSqftOnExistingHousehold() {
        Household existing = Household.builder().id(5L).apartment(apartment).houseNumber("A-101").build();
        when(householdRepository.findByApartmentAndHouseNumber(apartment, "A-101")).thenReturn(Optional.of(existing));

        Household result = householdService.findOrCreate(apartment, "A-101", new BigDecimal("1000.00"));

        assertThat(result.getFlatSizeSqft()).isEqualByComparingTo("1000.00");
    }

    @Test
    void findOrCreateLeavesExistingFlatSizeSqftUnchangedWhenNullPassed() {
        Household existing = Household.builder().id(5L).apartment(apartment).houseNumber("A-101")
                .flatSizeSqft(new BigDecimal("900.00")).build();
        when(householdRepository.findByApartmentAndHouseNumber(apartment, "A-101")).thenReturn(Optional.of(existing));

        Household result = householdService.findOrCreate(apartment, "A-101", null);

        assertThat(result.getFlatSizeSqft()).isEqualByComparingTo("900.00");
    }

    @Test
    void listForAdminMapsToResponses() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of(household));

        List<HouseholdResponse> responses = householdService.listForAdmin(apartment);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).houseNumber()).isEqualTo("A-101");
    }
}
