package com.hydroledger.backend.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.hydroledger.backend.dto.PlatformReportResponse;
import com.hydroledger.backend.dto.WaterUsageReadingResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.BillingCycle;
import com.hydroledger.backend.entity.BillingCycleStatus;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Invoice;
import com.hydroledger.backend.entity.ReadingSource;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.ApartmentRepository;
import com.hydroledger.backend.repository.BillingCycleRepository;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.repository.InvoiceRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.service.PlatformReportService;
import com.hydroledger.backend.service.WaterUsageReadingService;

@ExtendWith(MockitoExtension.class)
class PlatformReportServiceTests {

    @Mock
    private CommunityAdminRepository communityAdminRepository;

    @Mock
    private ApartmentRepository apartmentRepository;

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private ResidentRepository residentRepository;

    @Mock
    private BillingCycleRepository billingCycleRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private WaterUsageReadingService waterUsageReadingService;

    @InjectMocks
    private PlatformReportService platformReportService;

    private final CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();
    private final Apartment apartment = Apartment.builder().id(10L).name("Sunrise Towers").admin(admin).build();

    private WaterUsageReadingResponse readingFor(String month, BigDecimal consumption) {
        return new WaterUsageReadingResponse(1L, "MTR-0001", LocalDate.now(), new BigDecimal("100"),
                ReadingSource.MANUAL, consumption, null, month);
    }

    @Test
    void getReportDefaultsToThreeMonthsWhenNoParamsGiven() {
        when(communityAdminRepository.findAll()).thenReturn(List.of());

        PlatformReportResponse response = platformReportService.getReport(null, null, null);

        assertThat(response.range().key()).isEqualTo("3mo");
        assertThat(response.range().months()).hasSize(3);
        assertThat(response.range().months().get(2)).isEqualTo(YearMonth.now().toString());
    }

    @Test
    void getReportResolvesCustomRangeInclusive() {
        when(communityAdminRepository.findAll()).thenReturn(List.of());

        PlatformReportResponse response = platformReportService.getReport(null, "2026-01", "2026-03");

        assertThat(response.range().key()).isEqualTo("custom");
        assertThat(response.range().months()).containsExactly("2026-01", "2026-02", "2026-03");
    }

    @Test
    void getReportRejectsCustomRangeWhereFromAfterTo() {
        assertThatThrownBy(() -> platformReportService.getReport(null, "2026-05", "2026-01"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void getReportRejectsCustomRangeMissingOneBound() {
        assertThatThrownBy(() -> platformReportService.getReport(null, "2026-05", null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void getReportSkipsAdminsWithoutApartment() {
        when(communityAdminRepository.findAll()).thenReturn(List.of(admin));
        when(apartmentRepository.findByAdmin(admin)).thenReturn(Optional.empty());

        PlatformReportResponse response = platformReportService.getReport("3mo", null, null);

        assertThat(response.communities()).isEmpty();
        assertThat(response.summary().totalCommunities()).isZero();
    }

    @Test
    void getReportSumsConsumptionAndBilledAndComputesAverages() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-1").build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(household).build();

        String month1 = YearMonth.now().minusMonths(1).toString();
        String month2 = YearMonth.now().toString();

        when(communityAdminRepository.findAll()).thenReturn(List.of(admin));
        when(apartmentRepository.findByAdmin(admin)).thenReturn(Optional.of(apartment));
        when(residentRepository.findByCreatedByOrderByCreatedAtDesc(admin)).thenReturn(List.of(resident));
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of(household));
        when(waterUsageReadingService.listForHousehold(household)).thenReturn(List.of(
                readingFor(month1, new BigDecimal("10")),
                readingFor(month2, new BigDecimal("20"))));

        BillingCycle cycle = BillingCycle.builder().id(1L).apartment(apartment).periodMonth(month2)
                .status(BillingCycleStatus.FINALIZED).build();
        when(billingCycleRepository.findByApartmentOrderByPeriodMonthDesc(apartment)).thenReturn(List.of(cycle));
        Invoice invoice = Invoice.builder().id(1L).billingCycle(cycle).household(household)
                .consumptionUnits(new BigDecimal("20")).totalAmount(new BigDecimal("300.00")).generatedAt(Instant.now()).build();
        when(invoiceRepository.findByBillingCycle(cycle)).thenReturn(List.of(invoice));

        PlatformReportResponse response = platformReportService.getReport("3mo", null, null);

        assertThat(response.communities()).hasSize(1);
        PlatformReportResponse.CommunityReportRow row = response.communities().get(0);
        assertThat(row.totalConsumptionKl()).isEqualByComparingTo("30");
        assertThat(row.totalBilledAmount()).isEqualByComparingTo("300.00");
        assertThat(row.residentCount()).isEqualTo(1);
        assertThat(row.avgConsumptionPerResident()).isEqualByComparingTo("30");
        assertThat(row.avgBilledPerResident()).isEqualByComparingTo("300.00");

        assertThat(response.summary().totalCommunities()).isEqualTo(1);
        assertThat(response.summary().totalResidents()).isEqualTo(1);
        assertThat(response.summary().totalConsumptionKl()).isEqualByComparingTo("30");
        assertThat(response.summary().totalBilledAmount()).isEqualByComparingTo("300.00");

        PlatformReportResponse.MonthlyTrendPoint lastPoint = response.monthlyTrend().stream()
                .filter(p -> p.periodMonth().equals(month2)).findFirst().orElseThrow();
        assertThat(lastPoint.totalConsumptionKl()).isEqualByComparingTo("20");
        assertThat(lastPoint.totalBilledAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    void getReportIgnoresBillingCyclesOutsideTheSelectedRange() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-1").build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(household).build();

        when(communityAdminRepository.findAll()).thenReturn(List.of(admin));
        when(apartmentRepository.findByAdmin(admin)).thenReturn(Optional.of(apartment));
        when(residentRepository.findByCreatedByOrderByCreatedAtDesc(admin)).thenReturn(List.of(resident));
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of(household));
        when(waterUsageReadingService.listForHousehold(household)).thenReturn(List.of());

        BillingCycle oldCycle = BillingCycle.builder().id(1L).apartment(apartment).periodMonth("2020-01")
                .status(BillingCycleStatus.FINALIZED).build();
        when(billingCycleRepository.findByApartmentOrderByPeriodMonthDesc(apartment)).thenReturn(List.of(oldCycle));

        PlatformReportResponse response = platformReportService.getReport("3mo", null, null);

        assertThat(response.communities().get(0).totalBilledAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
