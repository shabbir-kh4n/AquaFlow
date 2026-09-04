package com.shabbir.backend.usage;

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

import com.shabbir.backend.dto.CsvUploadResultResponse;
import com.shabbir.backend.dto.WaterUsageReadingRequest;
import com.shabbir.backend.dto.WaterUsageReadingResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.CommunityAdmin;
import com.shabbir.backend.entity.Household;
import com.shabbir.backend.entity.ReadingSource;
import com.shabbir.backend.entity.Resident;
import com.shabbir.backend.entity.WaterMeter;
import com.shabbir.backend.entity.WaterUsageReading;
import com.shabbir.backend.exception.ApiException;
import com.shabbir.backend.repository.WaterMeterRepository;
import com.shabbir.backend.repository.WaterUsageReadingRepository;
import com.shabbir.backend.service.ApartmentService;
import com.shabbir.backend.service.WaterUsageReadingService;

@ExtendWith(MockitoExtension.class)
class WaterUsageReadingServiceTests {

    @Mock
    private WaterUsageReadingRepository waterUsageReadingRepository;

    @Mock
    private WaterMeterRepository waterMeterRepository;

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
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY);
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
}
