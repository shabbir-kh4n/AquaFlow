package com.shabbir.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shabbir.backend.dto.AlertConfigResponse;
import com.shabbir.backend.dto.AlertResponse;
import com.shabbir.backend.dto.WaterUsageReadingResponse;
import com.shabbir.backend.entity.Alert;
import com.shabbir.backend.entity.AlertStatus;
import com.shabbir.backend.entity.AlertType;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.Household;
import com.shabbir.backend.entity.Resident;
import com.shabbir.backend.entity.WaterMeter;
import com.shabbir.backend.exception.ApiException;
import com.shabbir.backend.repository.AlertRepository;
import com.shabbir.backend.repository.ApartmentRepository;
import com.shabbir.backend.repository.HouseholdRepository;
import com.shabbir.backend.repository.ResidentRepository;
import com.shabbir.backend.repository.WaterMeterRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AlertEngineService {

    private static final Logger log = LoggerFactory.getLogger(AlertEngineService.class);

    // Fewer than this many prior months of data isn't a meaningful sample to compute a
    // household's own mean/std-dev against, so the outlier check is skipped until there's enough history.
    private static final int MIN_HISTORY_MONTHS = 3;

    private final ApartmentRepository apartmentRepository;
    private final HouseholdRepository householdRepository;
    private final ResidentRepository residentRepository;
    private final WaterMeterRepository waterMeterRepository;
    private final WaterUsageReadingService waterUsageReadingService;
    private final AlertConfigService alertConfigService;
    private final AlertRepository alertRepository;

    @Scheduled(cron = "0 0 4 * * *")
    public void evaluateAllApartments() {
        for (Apartment apartment : apartmentRepository.findAll()) {
            try {
                evaluateApartment(apartment);
            } catch (Exception ex) {
                log.error("Alert evaluation failed for apartment {}", apartment.getId(), ex);
            }
        }
    }

    @Transactional
    public int evaluateApartment(Apartment apartment) {
        AlertConfigResponse config = alertConfigService.getOrDefault(apartment);
        if (!config.thresholdAlertsEnabled() && !config.outlierAlertsEnabled()) {
            return 0;
        }

        int touched = 0;

        for (Household household : householdRepository.findByApartmentOrderByHouseNumberAsc(apartment)) {
            // Same orphan-skip as BillingCycleService.finalizeCycle: a household row with no resident
            // is stale data, not a real occupied unit.
            if (residentRepository.findByHousehold(household).isEmpty()) {
                continue;
            }

            Map<String, BigDecimal> monthlyTotals = new TreeMap<>();
            for (WaterUsageReadingResponse reading : waterUsageReadingService.listForHousehold(household)) {
                monthlyTotals.merge(reading.consumptionMonth(), reading.consumption(), BigDecimal::add);
            }
            if (monthlyTotals.isEmpty()) {
                continue;
            }

            String targetMonth = resolveTargetMonth(monthlyTotals);
            BigDecimal targetConsumption = monthlyTotals.getOrDefault(targetMonth, BigDecimal.ZERO);

            if (config.thresholdAlertsEnabled() && config.monthlyThresholdKl() != null) {
                BigDecimal threshold = config.monthlyThresholdKl();
                boolean exceeds = targetConsumption.compareTo(threshold) > 0;
                if (upsertAlert(household, apartment, AlertType.THRESHOLD_EXCEEDED, targetMonth, targetConsumption,
                        threshold, exceeds,
                        () -> "Monthly consumption of " + scale(targetConsumption) + " kL exceeds the configured threshold of "
                                + scale(threshold) + " kL.")) {
                    touched++;
                }
            }

            if (config.outlierAlertsEnabled()) {
                List<BigDecimal> history = monthlyTotals.entrySet().stream()
                        .filter(entry -> entry.getKey().compareTo(targetMonth) < 0)
                        .map(Map.Entry::getValue)
                        .toList();

                if (history.size() >= MIN_HISTORY_MONTHS) {
                    BigDecimal mean = mean(history);
                    BigDecimal stdDev = sampleStdDev(history, mean);
                    if (stdDev.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal multiplier = config.outlierStdDevMultiplier();
                        BigDecimal referenceValue = mean.add(stdDev.multiply(multiplier));
                        boolean isOutlier = targetConsumption.compareTo(referenceValue) > 0;
                        if (upsertAlert(household, apartment, AlertType.STATISTICAL_OUTLIER, targetMonth, targetConsumption,
                                referenceValue, isOutlier,
                                () -> "Monthly consumption of " + scale(targetConsumption) + " kL is more than "
                                        + multiplier + "σ above this household's average of " + scale(mean) + " kL.")) {
                            touched++;
                        }
                    }
                }
            }
        }

        return touched;
    }

    public List<AlertResponse> listAlerts(Apartment apartment, AlertStatus statusFilter) {
        return alertRepository.findByApartmentOrderByCreatedAtDesc(apartment).stream()
                .filter(alert -> statusFilter == null || alert.getStatus() == statusFilter)
                .map(this::toResponse)
                .toList();
    }

    public List<AlertResponse> listForResident(Resident resident) {
        Household residentHousehold = resident.getHousehold();
        if (residentHousehold == null) {
            return List.of();
        }

        // Same lazy-loading precaution as TariffPlanService.estimateForResident: resident.getHousehold()
        // may be a detached lazy proxy loaded during JWT authentication.
        Household household = householdRepository.findWithApartmentById(residentHousehold.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Household not found"));

        return alertRepository.findByHouseholdOrderByCreatedAtDesc(household).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AlertResponse updateStatus(Apartment apartment, Long alertId, AlertStatus status) {
        Alert alert = alertRepository.findByIdAndApartment(alertId, apartment)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Alert not found"));

        alert.setStatus(status);
        alert.setResolvedAt(status == AlertStatus.ACTIVE ? null : Instant.now());

        return toResponse(alertRepository.save(alert));
    }

    private boolean upsertAlert(Household household, Apartment apartment, AlertType type, String periodMonth,
            BigDecimal consumptionValue, BigDecimal referenceValue, boolean conditionTrue, Supplier<String> messageSupplier) {
        Optional<Alert> existing = alertRepository.findByHouseholdAndPeriodMonthAndAlertType(household, periodMonth, type);

        if (conditionTrue) {
            // A human dismissal is a deliberate call not to be alerted about this exact period again -
            // never resurrect it just because the same condition still holds on a later run.
            if (existing.isPresent() && existing.get().getStatus() == AlertStatus.DISMISSED) {
                return false;
            }

            Alert alert = existing.orElseGet(() -> Alert.builder()
                    .household(household)
                    .apartment(apartment)
                    .alertType(type)
                    .periodMonth(periodMonth)
                    .build());
            alert.setConsumptionValue(consumptionValue);
            alert.setReferenceValue(referenceValue);
            alert.setMessage(messageSupplier.get());
            alert.setStatus(AlertStatus.ACTIVE);
            alert.setResolvedAt(null);
            alertRepository.save(alert);
            return true;
        }

        if (existing.isPresent() && existing.get().getStatus() == AlertStatus.ACTIVE) {
            Alert alert = existing.get();
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(Instant.now());
            alertRepository.save(alert);
            return true;
        }

        return false;
    }

    private String resolveTargetMonth(Map<String, BigDecimal> monthlyTotals) {
        String currentMonth = YearMonth.now().toString();
        return monthlyTotals.containsKey(currentMonth) ? currentMonth : Collections.max(monthlyTotals.keySet());
    }

    private BigDecimal mean(List<BigDecimal> values) {
        double average = values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        return BigDecimal.valueOf(average).setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal sampleStdDev(List<BigDecimal> values, BigDecimal mean) {
        if (values.size() < 2) {
            return BigDecimal.ZERO;
        }
        double meanValue = mean.doubleValue();
        double sumSquares = values.stream().mapToDouble(v -> Math.pow(v.doubleValue() - meanValue, 2)).sum();
        double variance = sumSquares / (values.size() - 1);
        return BigDecimal.valueOf(Math.sqrt(variance)).setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private AlertResponse toResponse(Alert alert) {
        Household household = alert.getHousehold();
        String residentName = residentRepository.findByHousehold(household).map(Resident::getFullName).orElse(null);
        String meterNumber = waterMeterRepository.findByHousehold(household).map(WaterMeter::getMeterNumber).orElse(null);

        return new AlertResponse(
                alert.getId(),
                household.getId(),
                household.getHouseNumber(),
                residentName,
                meterNumber,
                alert.getAlertType(),
                alert.getPeriodMonth(),
                alert.getConsumptionValue(),
                alert.getReferenceValue(),
                alert.getMessage(),
                alert.getStatus(),
                alert.getCreatedAt(),
                alert.getResolvedAt());
    }
}
