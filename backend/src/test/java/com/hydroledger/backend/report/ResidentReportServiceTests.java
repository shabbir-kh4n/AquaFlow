package com.hydroledger.backend.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.hydroledger.backend.dto.InvoiceResponse;
import com.hydroledger.backend.dto.ResidentReportResponse;
import com.hydroledger.backend.dto.WaterUsageReadingResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.ReadingSource;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.service.BillingCycleService;
import com.hydroledger.backend.service.ResidentReportService;
import com.hydroledger.backend.service.StatementPdfService;
import com.hydroledger.backend.service.WaterUsageReadingService;

@ExtendWith(MockitoExtension.class)
class ResidentReportServiceTests {

    @Mock private HouseholdRepository householdRepository;
    @Mock private BillingCycleService billingCycleService;
    @Mock private WaterUsageReadingService waterUsageReadingService;
    @Mock private StatementPdfService statementPdfService;

    @InjectMocks
    private ResidentReportService residentReportService;

    private final Apartment apartment = Apartment.builder().id(1L).name("Sunrise Towers").build();
    private final Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
    private final Resident resident = Resident.builder().id(1L).fullName("Jane").email("jane@example.com").household(household).build();

    private WaterUsageReadingResponse reading(String consumptionMonth, String consumption) {
        return new WaterUsageReadingResponse(1L, "MTR-0001", LocalDate.now(), BigDecimal.ZERO, ReadingSource.MANUAL,
                new BigDecimal(consumption), Instant.now(), consumptionMonth);
    }

    private InvoiceResponse invoice(long id, String period, String amount, boolean paid) {
        return new InvoiceResponse(id, 1L, "A-101", "Jane", "MTR-0001", "ACTIVE", null, period,
                BigDecimal.TEN, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(amount), new BigDecimal(amount), BigDecimal.ZERO,
                Instant.now(), paid, paid ? Instant.now() : null, List.of(), List.of());
    }

    @Test
    void customRangeSumsConsumptionAndBillingAndComparesWithPreviousPeriod() {
        when(householdRepository.findWithApartmentById(1L)).thenReturn(Optional.of(household));
        when(waterUsageReadingService.listForHousehold(household)).thenReturn(List.of(
                // Previous equal-length period (2026-01..2026-02): 20 kL total.
                reading("2026-01", "8"), reading("2026-02", "12"),
                // In range (2026-03..2026-04): 30 kL total, over 2 months with data.
                reading("2026-03", "10"), reading("2026-04", "15"), reading("2026-04", "5"),
                // Out of range entirely.
                reading("2026-06", "99")));
        when(billingCycleService.listInvoicesForResident(resident)).thenReturn(List.of(
                invoice(1L, "2026-03", "100.00", true),
                invoice(2L, "2026-04", "60.50", false),
                invoice(3L, "2026-06", "999.00", false)));

        ResidentReportResponse report = residentReportService.getReport(resident, null, "2026-03", "2026-04");

        assertThat(report.range().key()).isEqualTo("custom");
        assertThat(report.range().previousFrom()).isEqualTo("2026-01");
        assertThat(report.range().previousTo()).isEqualTo("2026-02");
        assertThat(report.months()).extracting(ResidentReportResponse.MonthRow::periodMonth).containsExactly("2026-03", "2026-04");

        ResidentReportResponse.Summary s = report.summary();
        assertThat(s.totalConsumptionKl()).isEqualByComparingTo("30");
        assertThat(s.totalBilledAmount()).isEqualByComparingTo("160.50");
        assertThat(s.totalPaidAmount()).isEqualByComparingTo("100.00");
        assertThat(s.outstandingAmount()).isEqualByComparingTo("60.50");
        assertThat(s.avgMonthlyConsumptionKl()).isEqualByComparingTo("15");
        assertThat(s.previousPeriodConsumptionKl()).isEqualByComparingTo("20");
        assertThat(s.consumptionChangePercent()).isEqualByComparingTo("50.0");

        ResidentReportResponse.MonthRow april = report.months().get(1);
        assertThat(april.consumptionKl()).isEqualByComparingTo("20");
        assertThat(april.billedAmount()).isEqualByComparingTo("60.50");
        assertThat(april.invoiced()).isTrue();
        assertThat(april.paid()).isFalse();
        assertThat(report.months().get(0).paid()).isTrue();
    }

    @Test
    void presetRangeCoversTheLastNMonthsIncludingCurrent() {
        when(householdRepository.findWithApartmentById(1L)).thenReturn(Optional.of(household));
        when(waterUsageReadingService.listForHousehold(household)).thenReturn(List.of());
        when(billingCycleService.listInvoicesForResident(resident)).thenReturn(List.of());

        ResidentReportResponse report = residentReportService.getReport(resident, "3mo", null, null);

        YearMonth now = YearMonth.now();
        assertThat(report.range().key()).isEqualTo("3mo");
        assertThat(report.months()).extracting(ResidentReportResponse.MonthRow::periodMonth)
                .containsExactly(now.minusMonths(2).toString(), now.minusMonths(1).toString(), now.toString());
        assertThat(report.summary().consumptionChangePercent()).isNull();
        assertThat(report.summary().avgMonthlyConsumptionKl()).isEqualByComparingTo("0");
        assertThat(report.months()).allSatisfy(row -> assertThat(row.invoiced()).isFalse());
    }

    @Test
    void rejectsHalfSpecifiedCustomRange() {
        assertThatThrownBy(() -> residentReportService.getReport(resident, null, "2026-01", null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsFromAfterTo() {
        assertThatThrownBy(() -> residentReportService.getReport(resident, null, "2026-05", "2026-01"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsMalformedMonth() {
        assertThatThrownBy(() -> residentReportService.getReport(resident, null, "2026-13", "2026-14"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void reportThrowsNotFoundWhenResidentHasNoHousehold() {
        Resident noHousehold = Resident.builder().id(2L).fullName("X").email("x@example.com").household(null).build();

        assertThatThrownBy(() -> residentReportService.getReport(noHousehold, "3mo", null, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void statementPdfUsesOnlyTheResidentsOwnInvoicesSortedByPeriod() {
        byte[] pdf = {1, 2, 3};
        when(householdRepository.findWithApartmentById(1L)).thenReturn(Optional.of(household));
        when(billingCycleService.listInvoicesForResident(resident)).thenReturn(List.of(
                invoice(2L, "2026-08", "50.00", false), invoice(1L, "2026-07", "40.00", true)));
        when(statementPdfService.generate(eq("Sunrise Towers"), eq("A-101"), eq("Jane"), anyList())).thenReturn(pdf);

        assertThat(residentReportService.generateStatementPdf(resident)).isEqualTo(pdf);

        ArgumentCaptor<List<InvoiceResponse>> captor = ArgumentCaptor.forClass(List.class);
        verify(statementPdfService).generate(eq("Sunrise Towers"), eq("A-101"), eq("Jane"), captor.capture());
        assertThat(captor.getValue()).extracting(InvoiceResponse::periodMonth).containsExactly("2026-07", "2026-08");
        // Ownership: invoices are only ever loaded via the resident-scoped lookup.
        verify(billingCycleService).listInvoicesForResident(resident);
    }

    @Test
    void statementPdfThrowsNotFoundWhenResidentHasNoHousehold() {
        Resident noHousehold = Resident.builder().id(2L).fullName("X").email("x@example.com").household(null).build();

        assertThatThrownBy(() -> residentReportService.generateStatementPdf(noHousehold))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }
}
