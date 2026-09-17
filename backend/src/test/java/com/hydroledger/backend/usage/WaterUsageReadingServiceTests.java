package com.hydroledger.backend.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.hydroledger.backend.dto.ConsumptionComparisonPoint;
import com.hydroledger.backend.dto.CsvUploadResultResponse;
import com.hydroledger.backend.dto.WaterUsageReadingRequest;
import com.hydroledger.backend.dto.WaterUsageReadingResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.ReadingSource;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.WaterMeter;
import com.hydroledger.backend.entity.WaterUsageReading;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.repository.WaterMeterRepository;
import com.hydroledger.backend.repository.WaterUsageReadingRepository;
import com.hydroledger.backend.service.ApartmentService;
import com.hydroledger.backend.service.WaterUsageReadingService;

@ExtendWith(MockitoExtension.class)
class WaterUsageReadingServiceTests {

    @Mock
    private WaterUsageReadingRepository waterUsageReadingRepository;

    @Mock
    private WaterMeterRepository waterMeterRepository;

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private ApartmentService apartmentService;

    @InjectMocks
    private WaterUsageReadingService waterUsageReadingService;

    private CommunityAdmin admin;
    private Apartment apartment;
    private WaterMeter meter;

    @BeforeEach
    void setUp() {
        admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();
        apartment = Apartment.builder().id(1L).name("Sunrise Towers").admin(admin).build();
        meter = WaterMeter.builder().id(1L).apartment(apartment).meterNumber("MTR-0001").build();
    }

    @Test
    void recordManualHappyPath() {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-0001")).thenReturn(Optional.of(meter));
        when(waterUsageReadingRepository.existsByMeterAndReadingDate(meter, LocalDate.of(2026, 1, 1))).thenReturn(false);
        when(waterUsageReadingRepository.save(any(WaterUsageReading.class))).thenAnswer(inv -> inv.getArgument(0));

        WaterUsageReadingResponse response = waterUsageReadingService.recordManual(
                new WaterUsageReadingRequest("MTR-0001", LocalDate.of(2026, 1, 1), new BigDecimal("100.000")), admin);

        assertThat(response.meterNumber()).isEqualTo("MTR-0001");
        assertThat(response.source()).isEqualTo(ReadingSource.MANUAL);
        assertThat(response.consumption()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void recordManualFirstReadingHasZeroConsumption() {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-0001")).thenReturn(Optional.of(meter));
        when(waterUsageReadingRepository.existsByMeterAndReadingDate(meter, LocalDate.of(2026, 1, 1))).thenReturn(false);
        when(waterUsageReadingRepository.findFirstByMeterAndReadingDateBeforeOrderByReadingDateDesc(meter, LocalDate.of(2026, 1, 1)))
                .thenReturn(Optional.empty());
        when(waterUsageReadingRepository.save(any(WaterUsageReading.class))).thenAnswer(inv -> inv.getArgument(0));

        WaterUsageReadingResponse response = waterUsageReadingService.recordManual(
                new WaterUsageReadingRequest("MTR-0001", LocalDate.of(2026, 1, 1), new BigDecimal("100.000")), admin);

        assertThat(response.consumption()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.consumptionMonth()).isEqualTo("2026-01");
    }

    @Test
    void recordManualSubsequentReadingConsumptionIsDelta() {
        WaterUsageReading previous = WaterUsageReading.builder().id(9L).meter(meter)
                .readingDate(LocalDate.of(2026, 1, 1)).readingValue(new BigDecimal("100.000")).build();
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-0001")).thenReturn(Optional.of(meter));
        when(waterUsageReadingRepository.existsByMeterAndReadingDate(meter, LocalDate.of(2026, 2, 1))).thenReturn(false);
        when(waterUsageReadingRepository.findFirstByMeterAndReadingDateBeforeOrderByReadingDateDesc(meter, LocalDate.of(2026, 2, 1)))
                .thenReturn(Optional.of(previous));
        when(waterUsageReadingRepository.save(any(WaterUsageReading.class))).thenAnswer(inv -> inv.getArgument(0));

        WaterUsageReadingResponse response = waterUsageReadingService.recordManual(
                new WaterUsageReadingRequest("MTR-0001", LocalDate.of(2026, 2, 1), new BigDecimal("135.500")), admin);

        assertThat(response.consumption()).isEqualByComparingTo(new BigDecimal("35.500"));
        // The reading is dated Feb 1, but the 35.5 kL it reports was used between Jan 1 and Feb 1 —
        // i.e. during January — so it must be attributed to January, not the month it was recorded in.
        assertThat(response.consumptionMonth()).isEqualTo("2026-01");
    }

    @Test
    void recordManualThrowsUnprocessableEntityWhenLowerThanPrevious() {
        WaterUsageReading previous = WaterUsageReading.builder().id(9L).meter(meter)
                .readingDate(LocalDate.of(2026, 1, 1)).readingValue(new BigDecimal("100.000")).build();
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-0001")).thenReturn(Optional.of(meter));
        when(waterUsageReadingRepository.existsByMeterAndReadingDate(meter, LocalDate.of(2026, 2, 1))).thenReturn(false);
        when(waterUsageReadingRepository.findFirstByMeterAndReadingDateBeforeOrderByReadingDateDesc(meter, LocalDate.of(2026, 2, 1)))
                .thenReturn(Optional.of(previous));

        assertThatThrownBy(() -> waterUsageReadingService.recordManual(
                new WaterUsageReadingRequest("MTR-0001", LocalDate.of(2026, 2, 1), new BigDecimal("50.000")), admin))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void recordManualThrowsConflictOnDuplicateDate() {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-0001")).thenReturn(Optional.of(meter));
        when(waterUsageReadingRepository.existsByMeterAndReadingDate(meter, LocalDate.of(2026, 1, 1))).thenReturn(true);

        assertThatThrownBy(() -> waterUsageReadingService.recordManual(
                new WaterUsageReadingRequest("MTR-0001", LocalDate.of(2026, 1, 1), BigDecimal.TEN), admin))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
    }

    @Test
    void recordManualThrowsNotFoundForUnknownMeter() {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> waterUsageReadingService.recordManual(
                new WaterUsageReadingRequest("MTR-9999", LocalDate.of(2026, 1, 1), BigDecimal.TEN), admin))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void bulkUploadCsvAllSuccess() {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-0001")).thenReturn(Optional.of(meter));
        when(waterUsageReadingRepository.existsByMeterAndReadingDate(any(), any())).thenReturn(false);
        when(waterUsageReadingRepository.save(any(WaterUsageReading.class))).thenAnswer(inv -> inv.getArgument(0));

        String csv = "MTR-0001,2026-01-01,100.000\nMTR-0001,2026-01-02,110.000\n";
        MockMultipartFile file = new MockMultipartFile("file", "readings.csv", "text/csv", csv.getBytes());

        CsvUploadResultResponse result = waterUsageReadingService.bulkUploadCsv(file, admin);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isZero();
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void bulkUploadCsvMixedSuccessAndFailureWithReasons() {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-0001")).thenReturn(Optional.of(meter));
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-9999")).thenReturn(Optional.empty());
        when(waterUsageReadingRepository.existsByMeterAndReadingDate(any(), any())).thenReturn(false);
        when(waterUsageReadingRepository.save(any(WaterUsageReading.class))).thenAnswer(inv -> inv.getArgument(0));

        String csv = "MTR-0001,2026-01-01,100.000\nMTR-9999,2026-01-01,100.000\n";
        MockMultipartFile file = new MockMultipartFile("file", "readings.csv", "text/csv", csv.getBytes());

        CsvUploadResultResponse result = waterUsageReadingService.bulkUploadCsv(file, admin);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).reason()).contains("Unknown meter number");
        assertThat(result.failures().get(0).rowNumber()).isEqualTo(2);
    }

    @Test
    void bulkUploadCsvMalformedRow() {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);

        String csv = "MTR-0001,not-a-date,100.000\n";
        MockMultipartFile file = new MockMultipartFile("file", "readings.csv", "text/csv", csv.getBytes());

        CsvUploadResultResponse result = waterUsageReadingService.bulkUploadCsv(file, admin);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successCount()).isZero();
        assertThat(result.failures().get(0).reason()).contains("Invalid reading date");
    }

    @Test
    void bulkUploadCsvInBatchDuplicateFailsSecondRow() {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-0001")).thenReturn(Optional.of(meter));
        when(waterUsageReadingRepository.existsByMeterAndReadingDate(meter, LocalDate.of(2026, 1, 1)))
                .thenReturn(false, true);
        when(waterUsageReadingRepository.save(any(WaterUsageReading.class))).thenAnswer(inv -> inv.getArgument(0));

        String csv = "MTR-0001,2026-01-01,100.000\nMTR-0001,2026-01-01,105.000\n";
        MockMultipartFile file = new MockMultipartFile("file", "readings.csv", "text/csv", csv.getBytes());

        CsvUploadResultResponse result = waterUsageReadingService.bulkUploadCsv(file, admin);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason()).contains("already exists");
    }

    @Test
    void bulkUploadCsvMonotonicViolationBecomesRowFailureNotHardError() {
        WaterUsageReading previous = WaterUsageReading.builder().id(9L).meter(meter)
                .readingDate(LocalDate.of(2026, 1, 1)).readingValue(new BigDecimal("100.000")).build();
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(waterMeterRepository.findByApartmentAndMeterNumber(apartment, "MTR-0001")).thenReturn(Optional.of(meter));
        when(waterUsageReadingRepository.existsByMeterAndReadingDate(meter, LocalDate.of(2026, 2, 1))).thenReturn(false);
        when(waterUsageReadingRepository.findFirstByMeterAndReadingDateBeforeOrderByReadingDateDesc(meter, LocalDate.of(2026, 2, 1)))
                .thenReturn(Optional.of(previous));

        String csv = "MTR-0001,2026-02-01,50.000\n";
        MockMultipartFile file = new MockMultipartFile("file", "readings.csv", "text/csv", csv.getBytes());

        CsvUploadResultResponse result = waterUsageReadingService.bulkUploadCsv(file, admin);

        assertThat(result.successCount()).isZero();
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason()).contains("cannot be lower than the previous recorded reading");
    }

    @Test
    void bulkUploadCsvUnreadableFileThrowsBadRequest() throws Exception {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);

        org.springframework.web.multipart.MultipartFile file = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getInputStream()).thenThrow(new java.io.IOException("boom"));

        assertThatThrownBy(() -> waterUsageReadingService.bulkUploadCsv(file, admin))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void getLatestForResidentReturnsEmptyWhenNoHousehold() {
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(null).build();

        assertThat(waterUsageReadingService.getLatestForResident(resident)).isEmpty();
    }

    @Test
    void getLatestForResidentReturnsEmptyWhenNoMeterForHousehold() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(household).build();
        when(waterMeterRepository.findByHousehold(household)).thenReturn(Optional.empty());

        assertThat(waterUsageReadingService.getLatestForResident(resident)).isEmpty();
    }

    @Test
    void getLatestForResidentReturnsLatestReading() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(household).build();
        WaterUsageReading latest = WaterUsageReading.builder().id(2L).meter(meter)
                .readingDate(LocalDate.of(2026, 2, 1)).readingValue(new BigDecimal("120.000")).build();
        when(waterMeterRepository.findByHousehold(household)).thenReturn(Optional.of(meter));
        when(waterUsageReadingRepository.findByMeterOrderByReadingDateDesc(meter)).thenReturn(List.of(latest));

        assertThat(waterUsageReadingService.getLatestForResident(resident)).contains(latest);
    }

    @Test
    void listForResidentReturnsEmptyWhenNoHousehold() {
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(null).build();

        assertThat(waterUsageReadingService.listForResident(resident)).isEmpty();
    }

    @Test
    void listForResidentReturnsReadingsOldestToNewest() {
        Household household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(household).build();
        WaterUsageReading first = WaterUsageReading.builder().id(1L).meter(meter)
                .readingDate(LocalDate.of(2026, 1, 1)).readingValue(new BigDecimal("100.000")).build();
        WaterUsageReading second = WaterUsageReading.builder().id(2L).meter(meter)
                .readingDate(LocalDate.of(2026, 2, 1)).readingValue(new BigDecimal("120.000")).build();
        when(waterMeterRepository.findByHousehold(household)).thenReturn(Optional.of(meter));
        when(waterUsageReadingRepository.findByMeterOrderByReadingDateAsc(meter)).thenReturn(List.of(first, second));
        when(waterUsageReadingRepository.findFirstByMeterAndReadingDateBeforeOrderByReadingDateDesc(meter, LocalDate.of(2026, 1, 1)))
                .thenReturn(Optional.empty());
        when(waterUsageReadingRepository.findFirstByMeterAndReadingDateBeforeOrderByReadingDateDesc(meter, LocalDate.of(2026, 2, 1)))
                .thenReturn(Optional.of(first));

        List<WaterUsageReadingResponse> responses = waterUsageReadingService.listForResident(resident);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).consumption()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(responses.get(1).consumption()).isEqualByComparingTo(new BigDecimal("20.000"));
    }

    @Test
    void getComparisonForResidentReturnsEmptyWhenNoHousehold() {
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(null).build();

        assertThat(waterUsageReadingService.getComparisonForResident(resident)).isEmpty();
    }

    @Test
    void getComparisonForResidentComputesApartmentAndSimilarSizedAverages() {
        Household myHousehold = Household.builder().id(1L).apartment(apartment).houseNumber("A-101")
                .flatSizeSqft(new BigDecimal("1000")).build();
        Household similarHousehold = Household.builder().id(2L).apartment(apartment).houseNumber("A-102")
                .flatSizeSqft(new BigDecimal("1050")).build();
        Household biggerHousehold = Household.builder().id(3L).apartment(apartment).houseNumber("A-103")
                .flatSizeSqft(new BigDecimal("5000")).build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(myHousehold).build();

        WaterMeter meter2 = WaterMeter.builder().id(2L).apartment(apartment).meterNumber("MTR-0002").build();
        WaterMeter meter3 = WaterMeter.builder().id(3L).apartment(apartment).meterNumber("MTR-0003").build();

        // Each meter has a zero-reading baseline dated Jan 1 (not returned by the household's own
        // reading list, only found via the "previous reading" lookup) so the Feb 1 reading's whole
        // value becomes its delta — attributed to January per the consumptionMonth convention.
        WaterUsageReading myBaseline = WaterUsageReading.builder().id(10L).meter(meter)
                .readingDate(LocalDate.of(2026, 1, 1)).readingValue(BigDecimal.ZERO).build();
        WaterUsageReading similarBaseline = WaterUsageReading.builder().id(11L).meter(meter2)
                .readingDate(LocalDate.of(2026, 1, 1)).readingValue(BigDecimal.ZERO).build();
        WaterUsageReading biggerBaseline = WaterUsageReading.builder().id(12L).meter(meter3)
                .readingDate(LocalDate.of(2026, 1, 1)).readingValue(BigDecimal.ZERO).build();

        WaterUsageReading myReading = WaterUsageReading.builder().id(1L).meter(meter)
                .readingDate(LocalDate.of(2026, 2, 1)).readingValue(new BigDecimal("10.000")).build();
        WaterUsageReading similarReading = WaterUsageReading.builder().id(2L).meter(meter2)
                .readingDate(LocalDate.of(2026, 2, 1)).readingValue(new BigDecimal("20.000")).build();
        WaterUsageReading biggerReading = WaterUsageReading.builder().id(3L).meter(meter3)
                .readingDate(LocalDate.of(2026, 2, 1)).readingValue(new BigDecimal("60.000")).build();

        when(householdRepository.findWithApartmentById(1L)).thenReturn(Optional.of(myHousehold));
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment))
                .thenReturn(List.of(myHousehold, similarHousehold, biggerHousehold));

        when(waterMeterRepository.findByHousehold(myHousehold)).thenReturn(Optional.of(meter));
        when(waterMeterRepository.findByHousehold(similarHousehold)).thenReturn(Optional.of(meter2));
        when(waterMeterRepository.findByHousehold(biggerHousehold)).thenReturn(Optional.of(meter3));

        when(waterUsageReadingRepository.findByMeterOrderByReadingDateAsc(meter)).thenReturn(List.of(myReading));
        when(waterUsageReadingRepository.findByMeterOrderByReadingDateAsc(meter2)).thenReturn(List.of(similarReading));
        when(waterUsageReadingRepository.findByMeterOrderByReadingDateAsc(meter3)).thenReturn(List.of(biggerReading));

        when(waterUsageReadingRepository.findFirstByMeterAndReadingDateBeforeOrderByReadingDateDesc(meter, LocalDate.of(2026, 2, 1)))
                .thenReturn(Optional.of(myBaseline));
        when(waterUsageReadingRepository.findFirstByMeterAndReadingDateBeforeOrderByReadingDateDesc(meter2, LocalDate.of(2026, 2, 1)))
                .thenReturn(Optional.of(similarBaseline));
        when(waterUsageReadingRepository.findFirstByMeterAndReadingDateBeforeOrderByReadingDateDesc(meter3, LocalDate.of(2026, 2, 1)))
                .thenReturn(Optional.of(biggerBaseline));

        List<ConsumptionComparisonPoint> points = waterUsageReadingService.getComparisonForResident(resident);

        assertThat(points).hasSize(1);
        ConsumptionComparisonPoint point = points.get(0);
        assertThat(point.month()).isEqualTo("2026-01");
        assertThat(point.household()).isEqualByComparingTo(new BigDecimal("10.000"));
        // (10 + 20 + 60) / 3
        assertThat(point.apartmentAverage()).isEqualByComparingTo(new BigDecimal("30.000"));
        // only the 1000 and 1050 sqft households fall within +/-20% of 1000 sqft: (10 + 20) / 2
        assertThat(point.similarSizedAverage()).isEqualByComparingTo(new BigDecimal("15.000"));
    }

    @Test
    void getComparisonForResidentFallsBackToApartmentAverageWhenNoSqftSet() {
        Household myHousehold = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        Household otherHousehold = Household.builder().id(2L).apartment(apartment).houseNumber("A-102").build();
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(myHousehold).build();

        WaterMeter meter2 = WaterMeter.builder().id(2L).apartment(apartment).meterNumber("MTR-0002").build();
        WaterUsageReading myReading = WaterUsageReading.builder().id(1L).meter(meter)
                .readingDate(LocalDate.of(2026, 2, 1)).readingValue(new BigDecimal("10.000")).build();
        WaterUsageReading otherReading = WaterUsageReading.builder().id(2L).meter(meter2)
                .readingDate(LocalDate.of(2026, 2, 1)).readingValue(new BigDecimal("20.000")).build();

        when(householdRepository.findWithApartmentById(1L)).thenReturn(Optional.of(myHousehold));
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment))
                .thenReturn(List.of(myHousehold, otherHousehold));
        when(waterMeterRepository.findByHousehold(myHousehold)).thenReturn(Optional.of(meter));
        when(waterMeterRepository.findByHousehold(otherHousehold)).thenReturn(Optional.of(meter2));
        when(waterUsageReadingRepository.findByMeterOrderByReadingDateAsc(meter)).thenReturn(List.of(myReading));
        when(waterUsageReadingRepository.findByMeterOrderByReadingDateAsc(meter2)).thenReturn(List.of(otherReading));
        when(waterUsageReadingRepository.findFirstByMeterAndReadingDateBeforeOrderByReadingDateDesc(any(), any()))
                .thenReturn(Optional.empty());

        List<ConsumptionComparisonPoint> points = waterUsageReadingService.getComparisonForResident(resident);

        assertThat(points).hasSize(1);
        // First-ever reading for each meter, so consumption is 0 for both — the assertion only
        // cares that similarSizedAverage falls back to apartmentAverage when sqft isn't set.
        assertThat(points.get(0).similarSizedAverage()).isEqualByComparingTo(points.get(0).apartmentAverage());
    }
}
