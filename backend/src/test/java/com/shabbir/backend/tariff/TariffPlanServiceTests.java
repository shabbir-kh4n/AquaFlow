package com.shabbir.backend.tariff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.shabbir.backend.dto.BillEstimateResponse;
import com.shabbir.backend.dto.TariffPlanRequest;
import com.shabbir.backend.dto.TariffPlanResponse;
import com.shabbir.backend.dto.TariffTierRequest;
import com.shabbir.backend.dto.TariffTierResponse;
import com.shabbir.backend.dto.TierChargeLine;
import com.shabbir.backend.dto.WaterUsageReadingResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.CommunityAdmin;
import com.shabbir.backend.entity.Household;
import com.shabbir.backend.entity.ReadingSource;
import com.shabbir.backend.entity.Resident;
import com.shabbir.backend.entity.TariffPlan;
import com.shabbir.backend.entity.TariffTier;
import com.shabbir.backend.exception.ApiException;
import com.shabbir.backend.repository.TariffPlanRepository;
import com.shabbir.backend.service.TariffPlanService;
import com.shabbir.backend.service.WaterUsageReadingService;

@ExtendWith(MockitoExtension.class)
class TariffPlanServiceTests {

    @Mock
    private TariffPlanRepository tariffPlanRepository;

    @Mock
    private com.shabbir.backend.repository.HouseholdRepository householdRepository;

    @Mock
    private WaterUsageReadingService waterUsageReadingService;

    @InjectMocks
    private TariffPlanService tariffPlanService;

    private CommunityAdmin admin;
    private Apartment apartment;

    private static final List<TariffTierResponse> THREE_TIERS = List.of(
            new TariffTierResponse(1, BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("5.00")),
            new TariffTierResponse(2, new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("8.00")),
            new TariffTierResponse(3, new BigDecimal("20"), null, new BigDecimal("12.00"))
    );

    @BeforeEach
    void setUp() {
        admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();
        apartment = Apartment.builder().id(1L).name("Sunrise Towers").admin(admin).build();
    }

    @Test
    void getOrDefaultReturnsUnpersistedDefaultsWhenNoneSaved() {
        when(tariffPlanRepository.findByApartment(apartment)).thenReturn(Optional.empty());

        TariffPlanResponse response = tariffPlanService.getOrDefault(apartment);

        assertThat(response.persisted()).isFalse();
        assertThat(response.tiers()).hasSize(3);
    }

    @Test
    void getOrDefaultReturnsPersistedPlan() {
        TariffPlan plan = TariffPlan.builder().id(5L).apartment(apartment).updatedAt(java.time.Instant.now()).build();
        plan.getTiers().add(TariffTier.builder().tariffPlan(plan).tierOrder(1)
                .minUnits(BigDecimal.ZERO).maxUnits(null).ratePerUnit(new BigDecimal("6.00")).build());
        when(tariffPlanRepository.findByApartment(apartment)).thenReturn(Optional.of(plan));

        TariffPlanResponse response = tariffPlanService.getOrDefault(apartment);

        assertThat(response.persisted()).isTrue();
        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.tiers()).hasSize(1);
    }

    @Test
    void saveCreatesNewPlanWithOrderedTiers() {
        when(tariffPlanRepository.findByApartment(apartment)).thenReturn(Optional.empty());
        when(tariffPlanRepository.save(any(TariffPlan.class))).thenAnswer(inv -> {
            TariffPlan plan = inv.getArgument(0);
            plan.setId(1L);
            plan.setUpdatedAt(java.time.Instant.now());
            return plan;
        });

        TariffPlanRequest request = new TariffPlanRequest(List.of(
                new TariffTierRequest(new BigDecimal("10"), null, new BigDecimal("8.00")),
                new TariffTierRequest(BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("5.00"))
        ));

        TariffPlanResponse response = tariffPlanService.save(apartment, request);

        assertThat(response.persisted()).isTrue();
        assertThat(response.tiers()).hasSize(2);
        assertThat(response.tiers().get(0).tierOrder()).isEqualTo(1);
        assertThat(response.tiers().get(0).minUnits()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.tiers().get(1).tierOrder()).isEqualTo(2);
    }

    @Test
    void saveFlushesOrphanedTierRemovalBeforeInsertingNewOnes() {
        TariffPlan existingPlan = TariffPlan.builder().id(2L).apartment(apartment).updatedAt(java.time.Instant.now()).build();
        existingPlan.getTiers().add(TariffTier.builder().tariffPlan(existingPlan).tierOrder(1)
                .minUnits(BigDecimal.ZERO).maxUnits(null).ratePerUnit(new BigDecimal("6.00")).build());
        when(tariffPlanRepository.findByApartment(apartment)).thenReturn(Optional.of(existingPlan));
        when(tariffPlanRepository.save(any(TariffPlan.class))).thenAnswer(inv -> {
            TariffPlan plan = inv.getArgument(0);
            plan.setUpdatedAt(java.time.Instant.now());
            return plan;
        });

        TariffPlanRequest request = new TariffPlanRequest(List.of(
                new TariffTierRequest(BigDecimal.ZERO, null, new BigDecimal("7.00"))
        ));

        // Re-saving an already-persisted plan with tiers reusing the same tier_order values must
        // flush the orphan-removal of the old tiers (uk_tariff_tiers_plan_order) before the new
        // tiers are inserted, or the insert collides with the not-yet-deleted old row.
        tariffPlanService.save(apartment, request);

        verify(tariffPlanRepository).saveAndFlush(existingPlan);
    }

    @Test
    void saveRejectsFirstTierNotStartingAtZero() {
        TariffPlanRequest request = new TariffPlanRequest(List.of(
                new TariffTierRequest(new BigDecimal("5"), null, new BigDecimal("5.00"))
        ));

        assertThatThrownBy(() -> tariffPlanService.save(apartment, request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void saveRejectsGapBetweenTiers() {
        TariffPlanRequest request = new TariffPlanRequest(List.of(
                new TariffTierRequest(BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("5.00")),
                new TariffTierRequest(new BigDecimal("15"), null, new BigDecimal("8.00"))
        ));

        assertThatThrownBy(() -> tariffPlanService.save(apartment, request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY)
                .hasMessageContaining("contiguous");
    }

    @Test
    void saveRejectsOverlappingTiers() {
        TariffPlanRequest request = new TariffPlanRequest(List.of(
                new TariffTierRequest(BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("5.00")),
                new TariffTierRequest(new BigDecimal("5"), null, new BigDecimal("8.00"))
        ));

        assertThatThrownBy(() -> tariffPlanService.save(apartment, request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void saveRejectsMoreThanOneUnboundedTier() {
        TariffPlanRequest request = new TariffPlanRequest(List.of(
                new TariffTierRequest(BigDecimal.ZERO, null, new BigDecimal("5.00")),
                new TariffTierRequest(new BigDecimal("10"), null, new BigDecimal("8.00"))
        ));

        assertThatThrownBy(() -> tariffPlanService.save(apartment, request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY)
                .hasMessageContaining("unbounded");
    }

    @Test
    void calculateAmountSingleTierWithinBand() {
        BigDecimal amount = tariffPlanService.calculateAmount(new BigDecimal("5"), THREE_TIERS);

        assertThat(amount).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    void calculateAmountCrossingMultipleTiers() {
        // 25 units => 10@5 + 10@8 + 5@12 = 50 + 80 + 60 = 190
        BigDecimal amount = tariffPlanService.calculateAmount(new BigDecimal("25"), THREE_TIERS);

        assertThat(amount).isEqualByComparingTo(new BigDecimal("190.00"));
    }

    @Test
    void calculateAmountExactBoundaryValue() {
        // exactly 10 units stays in tier 1
        BigDecimal amount = tariffPlanService.calculateAmount(new BigDecimal("10"), THREE_TIERS);

        assertThat(amount).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void calculateAmountZeroConsumptionIsZero() {
        BigDecimal amount = tariffPlanService.calculateAmount(BigDecimal.ZERO, THREE_TIERS);

        assertThat(amount).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateBreakdownReportsPerTierLines() {
        List<TierChargeLine> lines = tariffPlanService.calculateBreakdown(new BigDecimal("25"), THREE_TIERS);

        assertThat(lines).hasSize(3);
        assertThat(lines.get(0).unitsInTier()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(lines.get(1).unitsInTier()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(lines.get(2).unitsInTier()).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void estimateForResidentReturnsZeroWhenNoHousehold() {
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(null).build();

        BillEstimateResponse response = tariffPlanService.estimateForResident(resident, YearMonth.of(2026, 3));

        assertThat(response.consumptionUnits()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void estimateForResidentThrowsNotFoundWhenHouseholdMissing() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(household).build();
        when(householdRepository.findWithApartmentById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tariffPlanService.estimateForResident(resident, YearMonth.of(2026, 3)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void estimateForResidentSumsCurrentMonthConsumptionAndAppliesDefaultTariff() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(household).build();
        when(householdRepository.findWithApartmentById(1L)).thenReturn(Optional.of(household));
        when(tariffPlanRepository.findByApartment(apartment)).thenReturn(Optional.empty());
        when(waterUsageReadingService.listForResident(resident)).thenReturn(List.of(
                new WaterUsageReadingResponse(1L, "MTR-0001", LocalDate.of(2026, 3, 1),
                        new BigDecimal("100.000"), ReadingSource.MANUAL, new BigDecimal("5"), null, "2026-03"),
                new WaterUsageReadingResponse(2L, "MTR-0001", LocalDate.of(2026, 3, 15),
                        new BigDecimal("120.000"), ReadingSource.MANUAL, new BigDecimal("20"), null, "2026-03"),
                new WaterUsageReadingResponse(3L, "MTR-0001", LocalDate.of(2026, 2, 1),
                        new BigDecimal("80.000"), ReadingSource.MANUAL, new BigDecimal("999"), null, "2026-02")
        ));

        BillEstimateResponse response = tariffPlanService.estimateForResident(resident, YearMonth.of(2026, 3));

        // 25 units total (5 + 20) => 10@5 + 10@8 + 5@12 = 190
        assertThat(response.consumptionUnits()).isEqualByComparingTo(new BigDecimal("25"));
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("190.00"));
        assertThat(response.periodMonth()).isEqualTo("2026-03");
    }

    @Test
    void estimateForResidentFallsBackToLatestMonthWithReadingsWhenRequestedMonthHasNone() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(household).build();
        when(householdRepository.findWithApartmentById(1L)).thenReturn(Optional.of(household));
        when(tariffPlanRepository.findByApartment(apartment)).thenReturn(Optional.empty());
        when(waterUsageReadingService.listForResident(resident)).thenReturn(List.of(
                new WaterUsageReadingResponse(1L, "MTR-0001", LocalDate.of(2026, 2, 1),
                        new BigDecimal("80.000"), ReadingSource.MANUAL, new BigDecimal("10"), null, "2026-02"),
                new WaterUsageReadingResponse(2L, "MTR-0001", LocalDate.of(2026, 1, 1),
                        new BigDecimal("70.000"), ReadingSource.MANUAL, new BigDecimal("999"), null, "2026-01")
        ));

        // No readings exist for March 2026 at all — should fall back to February, the latest month with data.
        BillEstimateResponse response = tariffPlanService.estimateForResident(resident, YearMonth.of(2026, 3));

        assertThat(response.periodMonth()).isEqualTo("2026-02");
        assertThat(response.consumptionUnits()).isEqualByComparingTo(new BigDecimal("10"));
    }
}
