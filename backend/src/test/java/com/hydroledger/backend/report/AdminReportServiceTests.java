package com.hydroledger.backend.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.hydroledger.backend.dto.AdminReportResponse;
import com.hydroledger.backend.dto.AdminReportResponse.HouseholdReportRow;
import com.hydroledger.backend.dto.WaterUsageReadingResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.BillingCycle;
import com.hydroledger.backend.entity.BillingCycleStatus;
import com.hydroledger.backend.entity.BulkWaterPurchase;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Invoice;
import com.hydroledger.backend.entity.ReadingSource;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.WaterMeter;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.BillingCycleRepository;
import com.hydroledger.backend.repository.BulkWaterPurchaseRepository;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.repository.InvoiceRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.WaterMeterRepository;
import com.hydroledger.backend.service.AdminReportService;
import com.hydroledger.backend.service.WaterUsageReadingService;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceTests {

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private ResidentRepository residentRepository;

    @Mock
    private WaterMeterRepository waterMeterRepository;

    @Mock
    private BillingCycleRepository billingCycleRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private BulkWaterPurchaseRepository bulkWaterPurchaseRepository;

    @Mock
    private WaterUsageReadingService waterUsageReadingService;

    @InjectMocks
    private AdminReportService adminReportService;

    private final CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();
    private final Apartment apartment = Apartment.builder().id(10L).name("Sunrise Towers").admin(admin).build();

    private final String month1 = YearMonth.now().minusMonths(1).toString();
    private final String month2 = YearMonth.now().toString();

    private WaterUsageReadingResponse readingFor(String month, String consumption) {
        return new WaterUsageReadingResponse(1L, "MTR-0001", LocalDate.now(), new BigDecimal("100"),
                ReadingSource.MANUAL, new BigDecimal(consumption), null, month);
    }

    private Invoice invoice(long id, BillingCycle cycle, Household household, String total, String commonArea, boolean paid) {
        return Invoice.builder().id(id).billingCycle(cycle).household(household)
                .consumptionUnits(BigDecimal.TEN).totalAmount(new BigDecimal(total))
                .commonAreaAmount(new BigDecimal(commonArea)).paid(paid).generatedAt(Instant.now()).build();
    }

    private void stubEmptyApartment() {
        when(billingCycleRepository.findByApartmentOrderByPeriodMonthDesc(apartment)).thenReturn(List.of());
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of());
        when(bulkWaterPurchaseRepository.findByApartmentOrderByPurchaseDateDesc(apartment)).thenReturn(List.of());
    }

    @Test
    void defaultsToThreeMonthsWhenNoParamsGiven() {
        stubEmptyApartment();

        AdminReportResponse response = adminReportService.getReport(apartment, null, null, null);

        assertThat(response.range().key()).isEqualTo("3mo");
        assertThat(response.range().months()).hasSize(3);
        assertThat(response.range().to()).isEqualTo(YearMonth.now().toString());
        assertThat(response.apartmentName()).isEqualTo("Sunrise Towers");
        assertThat(response.summary().collectionRatePercent()).isEqualByComparingTo("0");
    }

    @Test
    void presetRangesResolveToTheirMonthCounts() {
        stubEmptyApartment();

        assertThat(adminReportService.getReport(apartment, "6mo", null, null).range().months()).hasSize(6);
        assertThat(adminReportService.getReport(apartment, "12mo", null, null).range().months()).hasSize(12);
    }

    @Test
    void resolvesCustomRangeInclusive() {
        stubEmptyApartment();

        AdminReportResponse response = adminReportService.getReport(apartment, null, "2026-01", "2026-03");

        assertThat(response.range().key()).isEqualTo("custom");
        assertThat(response.range().months()).containsExactly("2026-01", "2026-02", "2026-03");
    }

    @Test
    void rejectsCustomRangeWhereFromAfterTo() {
        assertThatThrownBy(() -> adminReportService.getReport(apartment, null, "2026-05", "2026-01"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsCustomRangeMissingOneBound() {
        assertThatThrownBy(() -> adminReportService.getReport(apartment, null, null, "2026-05"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsMalformedMonth() {
        assertThatThrownBy(() -> adminReportService.getReport(apartment, null, "2026-13", "2026-14"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void onlyQueriesTheCallersOwnApartment() {
        stubEmptyApartment();

        adminReportService.getReport(apartment, "3mo", null, null);

        verify(billingCycleRepository).findByApartmentOrderByPeriodMonthDesc(apartment);
        verify(householdRepository).findByApartmentOrderByHouseNumberAsc(apartment);
        verify(bulkWaterPurchaseRepository).findByApartmentOrderByPurchaseDateDesc(apartment);
        verify(householdRepository, never()).findAll();
        verify(billingCycleRepository, never()).findAll();
    }

    @Test
    void sumsConsumptionBillingCollectionCommonAreaAndBulkPurchases() {
        Household h1 = Household.builder().id(1L).apartment(apartment).houseNumber("A-1").build();
        Household h2 = Household.builder().id(2L).apartment(apartment).houseNumber("A-2").build();
        Resident r1 = Resident.builder().id(1L).fullName("Asha").email("a@example.com").household(h1).build();
        Resident r2 = Resident.builder().id(2L).fullName("Bala").email("b@example.com").household(h2).build();

        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of(h1, h2));
        when(residentRepository.findByHousehold(h1)).thenReturn(Optional.of(r1));
        when(residentRepository.findByHousehold(h2)).thenReturn(Optional.of(r2));
        when(waterMeterRepository.findByHousehold(any())).thenReturn(Optional.of(WaterMeter.builder().meterNumber("MTR-1").build()));
        when(waterUsageReadingService.listForHousehold(h1)).thenReturn(List.of(
                readingFor(month1, "10"), readingFor(month2, "20"), readingFor("2020-01", "999")));
        when(waterUsageReadingService.listForHousehold(h2)).thenReturn(List.of(readingFor(month2, "5")));

        BillingCycle finalized = BillingCycle.builder().id(1L).apartment(apartment).periodMonth(month1)
                .status(BillingCycleStatus.FINALIZED).build();
        BillingCycle archived = BillingCycle.builder().id(2L).apartment(apartment).periodMonth(month2)
                .status(BillingCycleStatus.ARCHIVED).build();
        BillingCycle open = BillingCycle.builder().id(3L).apartment(apartment).periodMonth(month2)
                .status(BillingCycleStatus.OPEN).build();
        BillingCycle outOfRange = BillingCycle.builder().id(4L).apartment(apartment).periodMonth("2020-01")
                .status(BillingCycleStatus.FINALIZED).build();
        when(billingCycleRepository.findByApartmentOrderByPeriodMonthDesc(apartment))
                .thenReturn(List.of(open, archived, finalized, outOfRange));
        when(invoiceRepository.findByBillingCycle(finalized)).thenReturn(List.of(
                invoice(1, finalized, h1, "300.00", "20.00", true),
                invoice(2, finalized, h2, "100.00", "10.00", false)));
        when(invoiceRepository.findByBillingCycle(archived)).thenReturn(List.of(
                invoice(3, archived, h1, "200.00", "15.00", false)));

        BulkWaterPurchase inRange = BulkWaterPurchase.builder().id(1L).apartment(apartment)
                .purchaseDate(YearMonth.now().atDay(1)).source("Tanker").volumeLiters(new BigDecimal("12000"))
                .unitCost(new BigDecimal("0.02")).totalCost(new BigDecimal("240.00")).build();
        BulkWaterPurchase oldPurchase = BulkWaterPurchase.builder().id(2L).apartment(apartment)
                .purchaseDate(LocalDate.of(2020, 1, 1)).source("Tanker").volumeLiters(new BigDecimal("5000"))
                .unitCost(new BigDecimal("0.02")).totalCost(new BigDecimal("100.00")).build();
        when(bulkWaterPurchaseRepository.findByApartmentOrderByPurchaseDateDesc(apartment)).thenReturn(List.of(inRange, oldPurchase));

        AdminReportResponse response = adminReportService.getReport(apartment, "3mo", null, null);

        AdminReportResponse.AdminReportSummary s = response.summary();
        assertThat(s.householdCount()).isEqualTo(2);
        assertThat(s.totalConsumptionKl()).isEqualByComparingTo("35");
        assertThat(s.avgMonthlyConsumptionKl()).isEqualByComparingTo("11.667");
        // OPEN and out-of-range cycles are excluded.
        assertThat(s.totalBilledAmount()).isEqualByComparingTo("600.00");
        assertThat(s.totalPaidAmount()).isEqualByComparingTo("300.00");
        assertThat(s.totalOutstandingAmount()).isEqualByComparingTo("300.00");
        assertThat(s.collectionRatePercent()).isEqualByComparingTo("50.00");
        assertThat(s.invoiceCount()).isEqualTo(3);
        assertThat(s.unpaidInvoiceCount()).isEqualTo(2);
        assertThat(s.commonAreaChargesTotal()).isEqualByComparingTo("45.00");
        assertThat(s.bulkPurchaseCount()).isEqualTo(1);
        assertThat(s.bulkPurchaseTotalCost()).isEqualByComparingTo("240.00");
        assertThat(s.bulkPurchaseVolumeKl()).isEqualByComparingTo("12");
        assertThat(s.billedMinusBulkCost()).isEqualByComparingTo("360.00");

        HouseholdReportRow row1 = response.households().get(0);
        assertThat(row1.houseNumber()).isEqualTo("A-1");
        assertThat(row1.residentName()).isEqualTo("Asha");
        assertThat(row1.meterNumber()).isEqualTo("MTR-1");
        assertThat(row1.totalConsumptionKl()).isEqualByComparingTo("30");
        assertThat(row1.avgMonthlyConsumptionKl()).isEqualByComparingTo("10");
        assertThat(row1.billedAmount()).isEqualByComparingTo("500.00");
        assertThat(row1.paidAmount()).isEqualByComparingTo("300.00");
        assertThat(row1.outstandingAmount()).isEqualByComparingTo("200.00");
        assertThat(row1.unpaidInvoiceCount()).isEqualTo(1);

        assertThat(response.topConsumers()).extracting(HouseholdReportRow::houseNumber).containsExactly("A-1", "A-2");

        AdminReportResponse.MonthlyTrendPoint current = response.monthlyTrend().stream()
                .filter(p -> p.periodMonth().equals(month2)).findFirst().orElseThrow();
        assertThat(current.totalConsumptionKl()).isEqualByComparingTo("25");
        assertThat(current.totalBilledAmount()).isEqualByComparingTo("200.00");
        assertThat(current.totalPaidAmount()).isEqualByComparingTo("0");
        assertThat(current.bulkPurchaseCost()).isEqualByComparingTo("240.00");
    }

    @Test
    void skipsHouseholdsWithNoResidentAndNothingInRange() {
        Household orphan = Household.builder().id(9L).apartment(apartment).houseNumber("Z-9").build();
        when(billingCycleRepository.findByApartmentOrderByPeriodMonthDesc(apartment)).thenReturn(List.of());
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of(orphan));
        when(bulkWaterPurchaseRepository.findByApartmentOrderByPurchaseDateDesc(apartment)).thenReturn(List.of());
        when(waterUsageReadingService.listForHousehold(orphan)).thenReturn(List.of());
        when(residentRepository.findByHousehold(orphan)).thenReturn(Optional.empty());

        AdminReportResponse response = adminReportService.getReport(apartment, "3mo", null, null);

        assertThat(response.households()).isEmpty();
        assertThat(response.topConsumers()).isEmpty();
    }
}
