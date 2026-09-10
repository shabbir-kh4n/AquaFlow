package com.shabbir.backend.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.shabbir.backend.dto.BillingCycleDetailResponse;
import com.shabbir.backend.dto.BillingCycleResponse;
import com.shabbir.backend.dto.CommonAreaConfigResponse;
import com.shabbir.backend.dto.CommonAreaCostItemResponse;
import com.shabbir.backend.dto.InvoiceResponse;
import com.shabbir.backend.dto.TariffPlanResponse;
import com.shabbir.backend.dto.TariffTierResponse;
import com.shabbir.backend.dto.WaterUsageReadingResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.BillingCycle;
import com.shabbir.backend.entity.BillingCycleStatus;
import com.shabbir.backend.entity.CommonAreaDistributionMethod;
import com.shabbir.backend.entity.CommunityAdmin;
import com.shabbir.backend.entity.Household;
import com.shabbir.backend.entity.Invoice;
import com.shabbir.backend.entity.ReadingSource;
import com.shabbir.backend.entity.Resident;
import com.shabbir.backend.exception.ApiException;
import com.shabbir.backend.repository.BillingCycleRepository;
import com.shabbir.backend.repository.HouseholdRepository;
import com.shabbir.backend.repository.InvoiceRepository;
import com.shabbir.backend.repository.ResidentRepository;
import com.shabbir.backend.repository.WaterMeterRepository;
import com.shabbir.backend.service.BillingCycleService;
import com.shabbir.backend.service.CommonAreaConfigService;
import com.shabbir.backend.service.TariffPlanService;
import com.shabbir.backend.service.WaterUsageReadingService;

@ExtendWith(MockitoExtension.class)
class BillingCycleServiceTests {

    @Mock
    private BillingCycleRepository billingCycleRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private ResidentRepository residentRepository;

    @Mock
    private WaterMeterRepository waterMeterRepository;

    @Mock
    private WaterUsageReadingService waterUsageReadingService;

    @Mock
    private TariffPlanService tariffPlanService;

    @Mock
    private CommonAreaConfigService commonAreaConfigService;

    @InjectMocks
    private BillingCycleService billingCycleService;

    private CommunityAdmin admin;
    private Apartment apartment;

    private static final List<TariffTierResponse> THREE_TIERS = List.of(
            new TariffTierResponse(1, BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("5.00")),
            new TariffTierResponse(2, new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("8.00")),
            new TariffTierResponse(3, new BigDecimal("20"), null, new BigDecimal("12.00"))
    );

    private static final CommonAreaConfigResponse NO_COMMON_AREA_CONFIG =
            new CommonAreaConfigResponse(null, CommonAreaDistributionMethod.CONSUMPTION, List.of(), false, null);

    @BeforeEach
    void setUp() {
        admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();
        apartment = Apartment.builder().id(1L).name("Sunrise Towers").admin(admin).build();
    }

    @Test
    void openCreatesNewOpenCycle() {
        when(billingCycleRepository.existsByApartmentAndPeriodMonth(apartment, "2026-09")).thenReturn(false);
        when(billingCycleRepository.save(any(BillingCycle.class))).thenAnswer(inv -> {
            BillingCycle cycle = inv.getArgument(0);
            cycle.setId(1L);
            return cycle;
        });

        BillingCycleResponse response = billingCycleService.open(apartment, YearMonth.of(2026, 9));

        assertThat(response.periodMonth()).isEqualTo("2026-09");
        assertThat(response.status()).isEqualTo(BillingCycleStatus.OPEN);
    }

    @Test
    void openRejectsDuplicateMonth() {
        when(billingCycleRepository.existsByApartmentAndPeriodMonth(apartment, "2026-09")).thenReturn(true);

        assertThatThrownBy(() -> billingCycleService.open(apartment, YearMonth.of(2026, 9)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
    }

    @Test
    void finalizeRejectsNonOpenCycle() {
        BillingCycle cycle = BillingCycle.builder().id(1L).apartment(apartment).periodMonth("2026-09")
                .status(BillingCycleStatus.FINALIZED).build();
        when(billingCycleRepository.findByIdAndApartment(1L, apartment)).thenReturn(Optional.of(cycle));

        assertThatThrownBy(() -> billingCycleService.finalizeCycle(apartment, 1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
    }

    @Test
    void finalizeGeneratesInvoicePerHouseholdWithTieredAmount() {
        BillingCycle cycle = BillingCycle.builder().id(1L).apartment(apartment).periodMonth("2026-09")
                .status(BillingCycleStatus.OPEN).build();
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(household).build();

        when(billingCycleRepository.findByIdAndApartment(1L, apartment)).thenReturn(Optional.of(cycle));
        when(tariffPlanService.getOrDefault(apartment)).thenReturn(new TariffPlanResponse(1L, THREE_TIERS, true, null));
        when(commonAreaConfigService.getOrDefault(apartment)).thenReturn(NO_COMMON_AREA_CONFIG);
        when(commonAreaConfigService.apportion(eq(NO_COMMON_AREA_CONFIG), any(), any())).thenReturn(java.util.Map.of());
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of(household));
        when(residentRepository.findByHousehold(household)).thenReturn(Optional.of(resident));
        when(waterUsageReadingService.listForHousehold(household)).thenReturn(List.of(
                new WaterUsageReadingResponse(1L, "MTR-0001", LocalDate.of(2026, 9, 1),
                        new BigDecimal("100.000"), ReadingSource.MANUAL, new BigDecimal("15"), null, "2026-09"),
                new WaterUsageReadingResponse(2L, "MTR-0001", LocalDate.of(2026, 9, 15),
                        new BigDecimal("115.000"), ReadingSource.MANUAL, new BigDecimal("10"), null, "2026-09")
        ));
        when(tariffPlanService.calculateBreakdown(any(BigDecimal.class), any()))
                .thenAnswer(inv -> tariffPlanServiceCalculateBreakdown(inv.getArgument(0)));
        when(billingCycleRepository.save(any(BillingCycle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.findByBillingCycle(cycle)).thenAnswer(inv -> cycle.getInvoices());

        BillingCycleDetailResponse detail = billingCycleService.finalizeCycle(apartment, 1L);

        assertThat(cycle.getStatus()).isEqualTo(BillingCycleStatus.FINALIZED);
        assertThat(detail.invoices()).hasSize(1);
        InvoiceResponse invoice = detail.invoices().get(0);
        // 25 units => 10@5 + 10@8 + 5@12 = 190
        assertThat(invoice.consumptionUnits()).isEqualByComparingTo(new BigDecimal("25"));
        assertThat(invoice.tariffAmount()).isEqualByComparingTo(new BigDecimal("190.00"));
        assertThat(invoice.commonAreaAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(invoice.totalAmount()).isEqualByComparingTo(new BigDecimal("190.00"));
        assertThat(invoice.houseNumber()).isEqualTo("A-101");
    }

    @Test
    void finalizeAddsCommonAreaChargesOnTopOfTariffAmount() {
        BillingCycle cycle = BillingCycle.builder().id(1L).apartment(apartment).periodMonth("2026-09")
                .status(BillingCycleStatus.OPEN).build();
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(household).build();

        CommonAreaConfigResponse commonAreaConfig = new CommonAreaConfigResponse(1L, CommonAreaDistributionMethod.CONSUMPTION,
                List.of(new CommonAreaCostItemResponse(1L, "Pool Maintenance", new BigDecimal("100.00"), true)), true, null);

        when(billingCycleRepository.findByIdAndApartment(1L, apartment)).thenReturn(Optional.of(cycle));
        when(tariffPlanService.getOrDefault(apartment)).thenReturn(new TariffPlanResponse(1L, THREE_TIERS, true, null));
        when(commonAreaConfigService.getOrDefault(apartment)).thenReturn(commonAreaConfig);
        when(commonAreaConfigService.apportion(eq(commonAreaConfig), any(), any())).thenReturn(java.util.Map.of(
                1L, List.of(new com.shabbir.backend.dto.CommonAreaChargeLine(1L, "Pool Maintenance", new BigDecimal("100.00")))));
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of(household));
        when(residentRepository.findByHousehold(household)).thenReturn(Optional.of(resident));
        when(waterUsageReadingService.listForHousehold(household)).thenReturn(List.of(
                new WaterUsageReadingResponse(1L, "MTR-0001", LocalDate.of(2026, 9, 1),
                        new BigDecimal("100.000"), ReadingSource.MANUAL, new BigDecimal("25"), null, "2026-09")
        ));
        when(tariffPlanService.calculateBreakdown(any(BigDecimal.class), any()))
                .thenAnswer(inv -> tariffPlanServiceCalculateBreakdown(inv.getArgument(0)));
        when(billingCycleRepository.save(any(BillingCycle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.findByBillingCycle(cycle)).thenAnswer(inv -> cycle.getInvoices());

        BillingCycleDetailResponse detail = billingCycleService.finalizeCycle(apartment, 1L);

        InvoiceResponse invoice = detail.invoices().get(0);
        // 25 units => 10@5 + 10@8 + 5@12 = 190, plus 100 common-area
        assertThat(invoice.tariffAmount()).isEqualByComparingTo(new BigDecimal("190.00"));
        assertThat(invoice.commonAreaAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(invoice.totalAmount()).isEqualByComparingTo(new BigDecimal("290.00"));
        assertThat(invoice.commonAreaCharges()).hasSize(1);
        assertThat(invoice.commonAreaCharges().get(0).label()).isEqualTo("Pool Maintenance");
    }

    @Test
    void finalizeSkipsHouseholdsWithoutAResident() {
        BillingCycle cycle = BillingCycle.builder().id(1L).apartment(apartment).periodMonth("2026-09")
                .status(BillingCycleStatus.OPEN).build();
        Household orphanedHousehold = Household.builder().id(2L).apartment(apartment).houseNumber("03").build();

        when(billingCycleRepository.findByIdAndApartment(1L, apartment)).thenReturn(Optional.of(cycle));
        when(tariffPlanService.getOrDefault(apartment)).thenReturn(new TariffPlanResponse(1L, THREE_TIERS, true, null));
        when(commonAreaConfigService.getOrDefault(apartment)).thenReturn(NO_COMMON_AREA_CONFIG);
        when(commonAreaConfigService.apportion(eq(NO_COMMON_AREA_CONFIG), any(), any())).thenReturn(java.util.Map.of());
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of(orphanedHousehold));
        when(residentRepository.findByHousehold(orphanedHousehold)).thenReturn(Optional.empty());
        when(billingCycleRepository.save(any(BillingCycle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.findByBillingCycle(cycle)).thenAnswer(inv -> cycle.getInvoices());

        BillingCycleDetailResponse detail = billingCycleService.finalizeCycle(apartment, 1L);

        assertThat(detail.invoices()).isEmpty();
    }

    @Test
    void archiveRejectsNonFinalizedCycle() {
        BillingCycle cycle = BillingCycle.builder().id(1L).apartment(apartment).periodMonth("2026-09")
                .status(BillingCycleStatus.OPEN).build();
        when(billingCycleRepository.findByIdAndApartment(1L, apartment)).thenReturn(Optional.of(cycle));

        assertThatThrownBy(() -> billingCycleService.archive(apartment, 1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
    }

    @Test
    void archiveSucceedsForFinalizedCycle() {
        BillingCycle cycle = BillingCycle.builder().id(1L).apartment(apartment).periodMonth("2026-09")
                .status(BillingCycleStatus.FINALIZED).build();
        when(billingCycleRepository.findByIdAndApartment(1L, apartment)).thenReturn(Optional.of(cycle));
        when(billingCycleRepository.save(any(BillingCycle.class))).thenAnswer(inv -> inv.getArgument(0));

        BillingCycleResponse response = billingCycleService.archive(apartment, 1L);

        assertThat(response.status()).isEqualTo(BillingCycleStatus.ARCHIVED);
    }

    @Test
    void setInvoicePaidMarksInvoicePaidWithTimestamp() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        BillingCycle cycle = BillingCycle.builder().id(1L).apartment(apartment).periodMonth("2026-09")
                .status(BillingCycleStatus.FINALIZED).build();
        Invoice invoice = Invoice.builder().id(5L).billingCycle(cycle).household(household)
                .consumptionUnits(BigDecimal.TEN).totalAmount(new BigDecimal("50.00"))
                .generatedAt(java.time.Instant.now()).paid(false).build();

        when(invoiceRepository.findByIdAndBillingCycle_Apartment(5L, apartment)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        InvoiceResponse response = billingCycleService.setInvoicePaid(apartment, 5L, true);

        assertThat(response.paid()).isTrue();
        assertThat(response.paidAt()).isNotNull();
    }

    @Test
    void setInvoicePaidClearsTimestampWhenMarkedUnpaid() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        BillingCycle cycle = BillingCycle.builder().id(1L).apartment(apartment).periodMonth("2026-09")
                .status(BillingCycleStatus.FINALIZED).build();
        Invoice invoice = Invoice.builder().id(5L).billingCycle(cycle).household(household)
                .consumptionUnits(BigDecimal.TEN).totalAmount(new BigDecimal("50.00"))
                .generatedAt(java.time.Instant.now()).paid(true).paidAt(java.time.Instant.now()).build();

        when(invoiceRepository.findByIdAndBillingCycle_Apartment(5L, apartment)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        InvoiceResponse response = billingCycleService.setInvoicePaid(apartment, 5L, false);

        assertThat(response.paid()).isFalse();
        assertThat(response.paidAt()).isNull();
    }

    @Test
    void setInvoicePaidThrowsNotFoundForUnknownInvoice() {
        when(invoiceRepository.findByIdAndBillingCycle_Apartment(5L, apartment)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingCycleService.setInvoicePaid(apartment, 5L, true))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void listInvoicesForResidentReturnsEmptyWhenNoHousehold() {
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(null).build();

        List<InvoiceResponse> invoices = billingCycleService.listInvoicesForResident(resident);

        assertThat(invoices).isEmpty();
    }

    private List<com.shabbir.backend.dto.TierChargeLine> tariffPlanServiceCalculateBreakdown(BigDecimal totalUnits) {
        List<com.shabbir.backend.dto.TierChargeLine> lines = new java.util.ArrayList<>();
        BigDecimal remaining = totalUnits;
        for (TariffTierResponse tier : THREE_TIERS) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal capacity = tier.maxUnits() == null ? remaining : tier.maxUnits().subtract(tier.minUnits());
            BigDecimal unitsInTier = remaining.min(capacity);
            BigDecimal amount = unitsInTier.multiply(tier.ratePerUnit());
            lines.add(new com.shabbir.backend.dto.TierChargeLine(tier.tierOrder(), unitsInTier, tier.ratePerUnit(), amount));
            remaining = remaining.subtract(unitsInTier);
        }
        return lines;
    }
}
