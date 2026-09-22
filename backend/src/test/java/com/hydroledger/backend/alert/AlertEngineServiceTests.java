package com.hydroledger.backend.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hydroledger.backend.dto.AlertConfigResponse;
import com.hydroledger.backend.dto.AlertResponse;
import com.hydroledger.backend.dto.WaterUsageReadingResponse;
import com.hydroledger.backend.entity.Alert;
import com.hydroledger.backend.entity.AlertStatus;
import com.hydroledger.backend.entity.AlertType;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.ReadingSource;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.repository.AlertRepository;
import com.hydroledger.backend.repository.ApartmentRepository;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.WaterMeterRepository;
import com.hydroledger.backend.service.AlertConfigService;
import com.hydroledger.backend.service.AlertEngineService;
import com.hydroledger.backend.service.EmailService;
import com.hydroledger.backend.service.WaterUsageReadingService;

@ExtendWith(MockitoExtension.class)
class AlertEngineServiceTests {

    @Mock
    private ApartmentRepository apartmentRepository;

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private ResidentRepository residentRepository;

    @Mock
    private WaterMeterRepository waterMeterRepository;

    @Mock
    private WaterUsageReadingService waterUsageReadingService;

    @Mock
    private AlertConfigService alertConfigService;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private com.hydroledger.backend.service.PushNotificationService pushNotificationService;

    @InjectMocks
    private AlertEngineService alertEngineService;

    private Apartment apartment;
    private Household household;
    private Resident resident;
    private final String currentMonth = YearMonth.now().toString();

    @BeforeEach
    void setUp() {
        apartment = Apartment.builder().id(1L).name("Sunrise Towers").build();
        household = Household.builder().id(1L).apartment(apartment).houseNumber("A-101").build();
        resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").household(household).build();
    }

    private void stubHouseholdWithResident() {
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of(household));
        when(residentRepository.findByHousehold(household)).thenReturn(Optional.of(resident));
    }

    private WaterUsageReadingResponse readingFor(String month, BigDecimal consumption) {
        return new WaterUsageReadingResponse(1L, "MTR-0001", LocalDate.now(), new BigDecimal("100"),
                ReadingSource.MANUAL, consumption, null, month);
    }

    @Test
    void evaluateApartmentSkipsHouseholdsWithoutAResident() {
        when(householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)).thenReturn(List.of(household));
        when(residentRepository.findByHousehold(household)).thenReturn(Optional.empty());
        when(alertConfigService.getOrDefault(apartment)).thenReturn(
                new AlertConfigResponse(1L, new BigDecimal("10"), new BigDecimal("2.0"), true, true, true, null));

        alertEngineService.evaluateApartment(apartment);

        verify(waterUsageReadingService, never()).listForHousehold(any());
    }

    @Test
    void evaluateApartmentDoesNothingWhenBothChecksDisabled() {
        when(alertConfigService.getOrDefault(apartment)).thenReturn(
                new AlertConfigResponse(1L, null, new BigDecimal("2.0"), false, false, true, null));

        int touched = alertEngineService.evaluateApartment(apartment);

        assertThat(touched).isZero();
        verify(waterUsageReadingService, never()).listForHousehold(any());
    }

    @Test
    void evaluateApartmentCreatesThresholdAlertWhenConsumptionExceedsLimit() {
        stubHouseholdWithResident();
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alertConfigService.getOrDefault(apartment)).thenReturn(
                new AlertConfigResponse(1L, new BigDecimal("10"), new BigDecimal("2.0"), true, false, true, null));
        when(waterUsageReadingService.listForHousehold(household)).thenReturn(List.of(
                readingFor(currentMonth, new BigDecimal("15"))));
        when(alertRepository.findByHouseholdAndPeriodMonthAndAlertType(household, currentMonth, AlertType.THRESHOLD_EXCEEDED))
                .thenReturn(Optional.empty());

        int touched = alertEngineService.evaluateApartment(apartment);

        assertThat(touched).isEqualTo(1);
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        Alert saved = captor.getValue();
        assertThat(saved.getAlertType()).isEqualTo(AlertType.THRESHOLD_EXCEEDED);
        assertThat(saved.getStatus()).isEqualTo(AlertStatus.ACTIVE);
        assertThat(saved.getConsumptionValue()).isEqualByComparingTo("15");
        assertThat(saved.getReferenceValue()).isEqualByComparingTo("10");
        // A brand-new alert (no prior row at all) is a genuine transition into ACTIVE -> notify.
        verify(emailService).sendAlertEmail(eq(resident), any(), any(), any());
        verify(pushNotificationService).sendNewAlertPush(eq(resident), any(), any(), any());
    }

    @Test
    void evaluateApartmentDoesNotReemailWhenAlertWasAlreadyActive() {
        Alert existing = Alert.builder().id(9L).household(household).apartment(apartment)
                .alertType(AlertType.THRESHOLD_EXCEEDED).periodMonth(currentMonth)
                .consumptionValue(new BigDecimal("15")).referenceValue(new BigDecimal("10"))
                .message("old").status(AlertStatus.ACTIVE).build();

        stubHouseholdWithResident();
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alertConfigService.getOrDefault(apartment)).thenReturn(
                new AlertConfigResponse(1L, new BigDecimal("10"), new BigDecimal("2.0"), true, false, true, null));
        when(waterUsageReadingService.listForHousehold(household)).thenReturn(List.of(
                readingFor(currentMonth, new BigDecimal("20"))));
        when(alertRepository.findByHouseholdAndPeriodMonthAndAlertType(household, currentMonth, AlertType.THRESHOLD_EXCEEDED))
                .thenReturn(Optional.of(existing));

        int touched = alertEngineService.evaluateApartment(apartment);

        assertThat(touched).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(AlertStatus.ACTIVE);
        // Still crossing on a re-run of an already-ACTIVE alert must not re-notify the resident.
        verify(emailService, never()).sendAlertEmail(any(), any(), any(), any());
        verify(pushNotificationService, never()).sendNewAlertPush(any(), any(), any(), any());
    }

    @Test
    void evaluateApartmentReemailsWhenAResolvedAlertBecomesActiveAgain() {
        Alert resolved = Alert.builder().id(9L).household(household).apartment(apartment)
                .alertType(AlertType.THRESHOLD_EXCEEDED).periodMonth(currentMonth)
                .consumptionValue(new BigDecimal("5")).referenceValue(new BigDecimal("10"))
                .message("old").status(AlertStatus.RESOLVED).resolvedAt(java.time.Instant.now()).build();

        stubHouseholdWithResident();
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alertConfigService.getOrDefault(apartment)).thenReturn(
                new AlertConfigResponse(1L, new BigDecimal("10"), new BigDecimal("2.0"), true, false, true, null));
        when(waterUsageReadingService.listForHousehold(household)).thenReturn(List.of(
                readingFor(currentMonth, new BigDecimal("20"))));
        when(alertRepository.findByHouseholdAndPeriodMonthAndAlertType(household, currentMonth, AlertType.THRESHOLD_EXCEEDED))
                .thenReturn(Optional.of(resolved));

        int touched = alertEngineService.evaluateApartment(apartment);

        assertThat(touched).isEqualTo(1);
        assertThat(resolved.getStatus()).isEqualTo(AlertStatus.ACTIVE);
        // Coming back from RESOLVED is a genuine transition into ACTIVE -> notify again.
        verify(emailService).sendAlertEmail(eq(resident), any(), any(), any());
        verify(pushNotificationService).sendNewAlertPush(eq(resident), any(), any(), any());
    }

    @Test
    void evaluateApartmentDoesNotFlagWhenUnderThreshold() {
        stubHouseholdWithResident();
        when(alertConfigService.getOrDefault(apartment)).thenReturn(
                new AlertConfigResponse(1L, new BigDecimal("10"), new BigDecimal("2.0"), true, false, true, null));
        when(waterUsageReadingService.listForHousehold(household)).thenReturn(List.of(
                readingFor(currentMonth, new BigDecimal("5"))));
        when(alertRepository.findByHouseholdAndPeriodMonthAndAlertType(household, currentMonth, AlertType.THRESHOLD_EXCEEDED))
                .thenReturn(Optional.empty());

        int touched = alertEngineService.evaluateApartment(apartment);

        assertThat(touched).isZero();
        verify(alertRepository, never()).save(any());
    }

    @Test
    void evaluateApartmentAutoResolvesActiveAlertWhenNoLongerExceeding() {
        Alert existing = Alert.builder().id(9L).household(household).apartment(apartment)
                .alertType(AlertType.THRESHOLD_EXCEEDED).periodMonth(currentMonth)
                .consumptionValue(new BigDecimal("15")).referenceValue(new BigDecimal("10"))
                .message("old").status(AlertStatus.ACTIVE).build();

        stubHouseholdWithResident();
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alertConfigService.getOrDefault(apartment)).thenReturn(
                new AlertConfigResponse(1L, new BigDecimal("10"), new BigDecimal("2.0"), true, false, true, null));
        when(waterUsageReadingService.listForHousehold(household)).thenReturn(List.of(
                readingFor(currentMonth, new BigDecimal("5"))));
        when(alertRepository.findByHouseholdAndPeriodMonthAndAlertType(household, currentMonth, AlertType.THRESHOLD_EXCEEDED))
                .thenReturn(Optional.of(existing));

        int touched = alertEngineService.evaluateApartment(apartment);

        assertThat(touched).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(existing.getResolvedAt()).isNotNull();
    }

    @Test
    void evaluateApartmentNeverReactivatesADismissedAlert() {
        Alert dismissed = Alert.builder().id(9L).household(household).apartment(apartment)
                .alertType(AlertType.THRESHOLD_EXCEEDED).periodMonth(currentMonth)
                .consumptionValue(new BigDecimal("15")).referenceValue(new BigDecimal("10"))
                .message("old").status(AlertStatus.DISMISSED).build();

        stubHouseholdWithResident();
        when(alertConfigService.getOrDefault(apartment)).thenReturn(
                new AlertConfigResponse(1L, new BigDecimal("10"), new BigDecimal("2.0"), true, false, true, null));
        when(waterUsageReadingService.listForHousehold(household)).thenReturn(List.of(
                readingFor(currentMonth, new BigDecimal("20"))));
        when(alertRepository.findByHouseholdAndPeriodMonthAndAlertType(household, currentMonth, AlertType.THRESHOLD_EXCEEDED))
                .thenReturn(Optional.of(dismissed));

        int touched = alertEngineService.evaluateApartment(apartment);

        assertThat(touched).isZero();
        verify(alertRepository, never()).save(any());
        assertThat(dismissed.getStatus()).isEqualTo(AlertStatus.DISMISSED);
    }

    @Test
    void evaluateApartmentSkipsOutlierCheckWithFewerThanThreeHistoryMonths() {
        stubHouseholdWithResident();
        when(alertConfigService.getOrDefault(apartment)).thenReturn(
                new AlertConfigResponse(1L, null, new BigDecimal("2.0"), false, true, true, null));
        when(waterUsageReadingService.listForHousehold(household)).thenReturn(List.of(
                readingFor("2026-06", new BigDecimal("10")),
                readingFor("2026-07", new BigDecimal("10")),
                readingFor(currentMonth, new BigDecimal("100"))));

        int touched = alertEngineService.evaluateApartment(apartment);

        assertThat(touched).isZero();
        verify(alertRepository, never()).save(any());
    }

    @Test
    void evaluateApartmentFlagsStatisticalOutlierAboveMeanPlusStdDev() {
        stubHouseholdWithResident();
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alertConfigService.getOrDefault(apartment)).thenReturn(
                new AlertConfigResponse(1L, null, new BigDecimal("2.0"), false, true, true, null));
        // Stable history of 10 kL for 4 months (mean 10, stddev 0 -> stddev check skips creating an
        // alert since the reference gate requires stdDev > 0), so mix in slight variance instead.
        when(waterUsageReadingService.listForHousehold(household)).thenReturn(List.of(
                readingFor("2026-05", new BigDecimal("9")),
                readingFor("2026-06", new BigDecimal("10")),
                readingFor("2026-07", new BigDecimal("11")),
                readingFor(currentMonth, new BigDecimal("100"))));
        when(alertRepository.findByHouseholdAndPeriodMonthAndAlertType(household, currentMonth, AlertType.STATISTICAL_OUTLIER))
                .thenReturn(Optional.empty());

        int touched = alertEngineService.evaluateApartment(apartment);

        assertThat(touched).isEqualTo(1);
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getAlertType()).isEqualTo(AlertType.STATISTICAL_OUTLIER);
        assertThat(captor.getValue().getConsumptionValue()).isEqualByComparingTo("100");
    }

    @Test
    void listAlertsFiltersByStatus() {
        Alert active = Alert.builder().id(1L).household(household).apartment(apartment)
                .alertType(AlertType.THRESHOLD_EXCEEDED).periodMonth(currentMonth)
                .consumptionValue(BigDecimal.TEN).referenceValue(BigDecimal.ONE).message("m").status(AlertStatus.ACTIVE).build();
        Alert resolved = Alert.builder().id(2L).household(household).apartment(apartment)
                .alertType(AlertType.THRESHOLD_EXCEEDED).periodMonth(currentMonth)
                .consumptionValue(BigDecimal.TEN).referenceValue(BigDecimal.ONE).message("m").status(AlertStatus.RESOLVED).build();
        when(alertRepository.findByApartmentOrderByCreatedAtDesc(apartment)).thenReturn(List.of(active, resolved));

        List<AlertResponse> result = alertEngineService.listAlerts(apartment, AlertStatus.ACTIVE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    void updateStatusSetsResolvedAtWhenLeavingActive() {
        Alert alert = Alert.builder().id(1L).household(household).apartment(apartment)
                .alertType(AlertType.THRESHOLD_EXCEEDED).periodMonth(currentMonth)
                .consumptionValue(BigDecimal.TEN).referenceValue(BigDecimal.ONE).message("m").status(AlertStatus.ACTIVE).build();
        when(alertRepository.findByIdAndApartment(1L, apartment)).thenReturn(Optional.of(alert));
        when(alertRepository.save(alert)).thenReturn(alert);

        AlertResponse response = alertEngineService.updateStatus(apartment, 1L, AlertStatus.DISMISSED);

        assertThat(response.status()).isEqualTo(AlertStatus.DISMISSED);
        assertThat(alert.getResolvedAt()).isNotNull();
    }

    @Test
    void listForResidentReturnsEmptyWhenNoHousehold() {
        Resident noHouseholdResident = Resident.builder().id(2L).fullName("Res2").email("r2@example.com").build();

        List<AlertResponse> result = alertEngineService.listForResident(noHouseholdResident);

        assertThat(result).isEmpty();
        verify(householdRepository, never()).findWithApartmentById(any());
    }

    @Test
    void listForResidentReturnsHouseholdScopedAlerts() {
        when(householdRepository.findWithApartmentById(1L)).thenReturn(Optional.of(household));
        Alert alert = Alert.builder().id(1L).household(household).apartment(apartment)
                .alertType(AlertType.THRESHOLD_EXCEEDED).periodMonth(currentMonth)
                .consumptionValue(BigDecimal.TEN).referenceValue(BigDecimal.ONE).message("m").status(AlertStatus.ACTIVE).build();
        when(alertRepository.findByHouseholdOrderByCreatedAtDesc(household)).thenReturn(List.of(alert));

        List<AlertResponse> result = alertEngineService.listForResident(resident);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).houseNumber()).isEqualTo("A-101");
    }
}
