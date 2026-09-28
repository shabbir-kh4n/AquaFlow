package com.hydroledger.backend.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hydroledger.backend.dto.PeerRankingResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.WaterMeter;
import com.hydroledger.backend.entity.WaterUsageReading;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.repository.WaterMeterRepository;
import com.hydroledger.backend.repository.WaterUsageReadingRepository;
import com.hydroledger.backend.service.ApartmentService;
import com.hydroledger.backend.service.WaterUsageReadingService;

@ExtendWith(MockitoExtension.class)
class PeerRankingServiceTests {

    @Mock private WaterUsageReadingRepository waterUsageReadingRepository;
    @Mock private WaterMeterRepository waterMeterRepository;
    @Mock private HouseholdRepository householdRepository;
    @Mock private ApartmentService apartmentService;

    @InjectMocks
    private WaterUsageReadingService waterUsageReadingService;

    private final Apartment apartment = Apartment.builder().id(1L).name("Sunrise Towers").build();

    private static Map<Long, BigDecimal> values(Object... idValuePairs) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (int i = 0; i < idValuePairs.length; i += 2) {
            map.put(((Number) idValuePairs[i]).longValue(), new BigDecimal((String) idValuePairs[i + 1]));
        }
        return map;
    }

    @Test
    void ranksLowerConsumptionBetterAndComputesStats() {
        PeerRankingResponse r = WaterUsageReadingService.buildPeerRanking("2026-02", 1L,
                values(1, "10", 2, "20", 3, "5", 4, "40"));

        assertThat(r.month()).isEqualTo("2026-02");
        assertThat(r.rank()).isEqualTo(2);
        assertThat(r.totalHouseholds()).isEqualTo(4);
        // 2 of the 3 other households used more -> 66.7%.
        assertThat(r.percentile()).isEqualByComparingTo("66.7");
        assertThat(r.householdConsumption()).isEqualByComparingTo("10");
        assertThat(r.apartmentMin()).isEqualByComparingTo("5");
        assertThat(r.apartmentMedian()).isEqualByComparingTo("15");
        assertThat(r.apartmentMax()).isEqualByComparingTo("40");
        assertThat(r.entries()).extracting(PeerRankingResponse.Entry::rank).containsExactly(1, 2, 3, 4);
        assertThat(r.entries()).extracting(PeerRankingResponse.Entry::self).containsExactly(false, true, false, false);
    }

    @Test
    void tiesShareARankAndTheCallerLeadsItsTieGroup() {
        PeerRankingResponse r = WaterUsageReadingService.buildPeerRanking("2026-02", 3L,
                values(1, "10", 2, "10", 3, "10", 4, "30"));

        assertThat(r.rank()).isEqualTo(1);
        assertThat(r.entries()).extracting(PeerRankingResponse.Entry::rank).containsExactly(1, 1, 1, 4);
        assertThat(r.entries().get(0).self()).isTrue();
        // Only household 4 used strictly more: 1 of 3 others.
        assertThat(r.percentile()).isEqualByComparingTo("33.3");
        assertThat(r.apartmentMedian()).isEqualByComparingTo("10");
    }

    @Test
    void singleHouseholdApartmentHasNoPercentile() {
        PeerRankingResponse r = WaterUsageReadingService.buildPeerRanking("2026-02", 1L, values(1, "12.5"));

        assertThat(r.rank()).isEqualTo(1);
        assertThat(r.totalHouseholds()).isEqualTo(1);
        assertThat(r.percentile()).isNull();
        assertThat(r.apartmentMin()).isEqualByComparingTo("12.5");
        assertThat(r.apartmentMedian()).isEqualByComparingTo("12.5");
        assertThat(r.apartmentMax()).isEqualByComparingTo("12.5");
        assertThat(r.entries()).hasSize(1);
    }

    @Test
    void responseShapeCarriesNoHouseholdIdentifiers() {
        List<String> topLevel = Arrays.stream(PeerRankingResponse.class.getRecordComponents()).map(RecordComponent::getName).toList();
        List<String> entry = Arrays.stream(PeerRankingResponse.Entry.class.getRecordComponents()).map(RecordComponent::getName).toList();

        assertThat(entry).containsExactlyInAnyOrder("rank", "consumption", "self");
        // Pin the exact shape so adding e.g. a houseNumber/householdId field fails this privacy test.
        assertThat(topLevel).containsExactly("month", "rank", "totalHouseholds", "percentile", "householdConsumption",
                "apartmentMin", "apartmentMedian", "apartmentMax", "entries");
    }

    @Test
    void returnsEmptyWhenResidentHasNoHousehold() {
        Resident resident = Resident.builder().id(1L).fullName("Res").email("r@example.com").household(null).build();

        PeerRankingResponse r = waterUsageReadingService.getPeerRankingForResident(resident);

        assertThat(r.rank()).isNull();
        assertThat(r.month()).isNull();
        assertThat(r.entries()).isEmpty();
    }

    @Test
    void returnsEmptyWhenResidentsHouseholdHasNoReadings() {
        Household mine = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("r@example.com").household(mine).build();
        when(householdRepository.findWithApartmentById(1L)).thenReturn(Optional.of(mine));
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of(mine));
        when(waterMeterRepository.findByHousehold(mine)).thenReturn(Optional.empty());

        PeerRankingResponse r = waterUsageReadingService.getPeerRankingForResident(resident);

        assertThat(r.rank()).isNull();
        assertThat(r.totalHouseholds()).isZero();
    }

    @Test
    void usesTheLatestMonthTheResidentHasDataForAndOnlyHouseholdsWithDataThatMonth() {
        Household mine = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        Household neighbour = Household.builder().id(2L).apartment(apartment).houseNumber("A-102").build();
        Household noMeter = Household.builder().id(3L).apartment(apartment).houseNumber("A-103").build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("r@example.com").household(mine).build();
        WaterMeter myMeter = WaterMeter.builder().id(1L).apartment(apartment).meterNumber("MTR-1").build();
        WaterMeter theirMeter = WaterMeter.builder().id(2L).apartment(apartment).meterNumber("MTR-2").build();

        // Mine: Jan 1 (0) -> Feb 1 (10) -> Mar 1 (25): January 10 kL, February 15 kL.
        WaterUsageReading m1 = reading(11L, myMeter, LocalDate.of(2026, 1, 1), "0");
        WaterUsageReading m2 = reading(12L, myMeter, LocalDate.of(2026, 2, 1), "10");
        WaterUsageReading m3 = reading(13L, myMeter, LocalDate.of(2026, 3, 1), "25");
        // Neighbour: Jan 1 (0) -> Feb 1 (30): January only, 30 kL.
        WaterUsageReading t1 = reading(21L, theirMeter, LocalDate.of(2026, 1, 1), "0");
        WaterUsageReading t2 = reading(22L, theirMeter, LocalDate.of(2026, 2, 1), "30");

        when(householdRepository.findWithApartmentById(1L)).thenReturn(Optional.of(mine));
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of(mine, neighbour, noMeter));
        when(waterMeterRepository.findByHousehold(mine)).thenReturn(Optional.of(myMeter));
        when(waterMeterRepository.findByHousehold(neighbour)).thenReturn(Optional.of(theirMeter));
        when(waterMeterRepository.findByHousehold(noMeter)).thenReturn(Optional.empty());
        when(waterUsageReadingRepository.findByMeterOrderByReadingDateAsc(myMeter)).thenReturn(List.of(m1, m2, m3));
        when(waterUsageReadingRepository.findByMeterOrderByReadingDateAsc(theirMeter)).thenReturn(List.of(t1, t2));
        when(waterUsageReadingRepository.findFirstByMeterAndReadingDateBeforeOrderByReadingDateDesc(any(), any()))
                .thenAnswer(inv -> {
                    WaterMeter meter = inv.getArgument(0);
                    LocalDate date = inv.getArgument(1);
                    List<WaterUsageReading> all = meter == myMeter ? List.of(m1, m2, m3) : List.of(t1, t2);
                    return all.stream().filter(r -> r.getReadingDate().isBefore(date))
                            .reduce((a, b) -> b);
                });

        PeerRankingResponse r = waterUsageReadingService.getPeerRankingForResident(resident);

        // February: only my household has data (the neighbour's last delta is January's).
        assertThat(r.month()).isEqualTo("2026-02");
        assertThat(r.totalHouseholds()).isEqualTo(1);
        assertThat(r.householdConsumption()).isEqualByComparingTo("15");
    }

    private static WaterUsageReading reading(long id, WaterMeter meter, LocalDate date, String value) {
        return WaterUsageReading.builder().id(id).meter(meter).readingDate(date).readingValue(new BigDecimal(value)).build();
    }
}
