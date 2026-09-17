package com.hydroledger.backend.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hydroledger.backend.dto.BillEstimateResponse;
import com.hydroledger.backend.dto.TariffPlanRequest;
import com.hydroledger.backend.dto.TariffPlanResponse;
import com.hydroledger.backend.dto.TariffTierRequest;
import com.hydroledger.backend.dto.TariffTierResponse;
import com.hydroledger.backend.dto.TierChargeLine;
import com.hydroledger.backend.dto.WaterUsageReadingResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.TariffPlan;
import com.hydroledger.backend.entity.TariffTier;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.HouseholdRepository;
import com.hydroledger.backend.repository.TariffPlanRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TariffPlanService {

    private static final List<TariffTierResponse> DEFAULT_TIERS = List.of(
            new TariffTierResponse(1, BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("5.00")),
            new TariffTierResponse(2, new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("8.00")),
            new TariffTierResponse(3, new BigDecimal("20"), null, new BigDecimal("12.00"))
    );

    private final TariffPlanRepository tariffPlanRepository;
    private final HouseholdRepository householdRepository;
    private final WaterUsageReadingService waterUsageReadingService;

    public TariffPlanResponse getOrDefault(Apartment apartment) {
        return tariffPlanRepository.findByApartment(apartment)
                .map(this::toResponse)
                .orElseGet(() -> new TariffPlanResponse(null, DEFAULT_TIERS, false, null));
    }

    @Transactional
    public TariffPlanResponse save(Apartment apartment, TariffPlanRequest request) {
        List<TariffTierRequest> sorted = request.tiers().stream()
                .sorted((a, b) -> a.minUnits().compareTo(b.minUnits()))
                .toList();

        validateTiers(sorted);

        TariffPlan plan = tariffPlanRepository.findByApartment(apartment)
                .orElseGet(() -> TariffPlan.builder().apartment(apartment).build());

        // Clear + flush before adding the new tiers: Hibernate flushes inserts before deletes within
        // a single flush, so re-adding tiers with the same tier_order as the ones just orphaned would
        // collide with uk_tariff_tiers_plan_order unless the deletes are flushed to the DB first.
        plan.getTiers().clear();
        tariffPlanRepository.saveAndFlush(plan);

        for (int i = 0; i < sorted.size(); i++) {
            TariffTierRequest tierRequest = sorted.get(i);
            plan.getTiers().add(TariffTier.builder()
                    .tariffPlan(plan)
                    .tierOrder(i + 1)
                    .minUnits(tierRequest.minUnits())
                    .maxUnits(tierRequest.maxUnits())
                    .ratePerUnit(tierRequest.ratePerUnit())
                    .build());
        }

        return toResponse(tariffPlanRepository.save(plan));
    }

    private void validateTiers(List<TariffTierRequest> sorted) {
        if (sorted.get(0).minUnits().compareTo(BigDecimal.ZERO) != 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "The first tier must start at 0 units");
        }

        for (int i = 0; i < sorted.size(); i++) {
            TariffTierRequest tier = sorted.get(i);
            boolean isLast = i == sorted.size() - 1;

            if (!isLast && tier.maxUnits() == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "Only the last tier may be unbounded (no maximum)");
            }

            if (tier.maxUnits() != null && tier.maxUnits().compareTo(tier.minUnits()) <= 0) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "Tier maximum must be greater than its minimum");
            }

            if (!isLast) {
                TariffTierRequest next = sorted.get(i + 1);
                if (tier.maxUnits().compareTo(next.minUnits()) != 0) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                            "Tiers must be contiguous: tier " + (i + 1) + "'s maximum must equal tier " + (i + 2) + "'s minimum");
                }
            }
        }
    }

    public BigDecimal calculateAmount(BigDecimal totalUnits, List<TariffTierResponse> tiers) {
        return calculateBreakdown(totalUnits, tiers).stream()
                .map(TierChargeLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<TierChargeLine> calculateBreakdown(BigDecimal totalUnits, List<TariffTierResponse> tiers) {
        List<TierChargeLine> lines = new ArrayList<>();
        BigDecimal remaining = totalUnits;

        for (TariffTierResponse tier : tiers) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal tierCapacity = tier.maxUnits() == null
                    ? remaining
                    : tier.maxUnits().subtract(tier.minUnits());

            BigDecimal unitsInTier = remaining.min(tierCapacity);
            BigDecimal amount = unitsInTier.multiply(tier.ratePerUnit());

            lines.add(new TierChargeLine(tier.tierOrder(), unitsInTier, tier.ratePerUnit(), amount));
            remaining = remaining.subtract(unitsInTier);
        }

        return lines;
    }

    public BillEstimateResponse estimateForResident(Resident resident, YearMonth month) {
        Household residentHousehold = resident.getHousehold();
        if (residentHousehold == null) {
            return new BillEstimateResponse(month.toString(), BigDecimal.ZERO, List.of(), BigDecimal.ZERO);
        }

        // resident.getHousehold() may be a detached lazy proxy by the time this runs (loaded
        // during JWT authentication, outside this method's transaction) — re-fetch with the
        // apartment join-fetched rather than dereferencing it directly, which would throw
        // LazyInitializationException.
        Household household = householdRepository.findWithApartmentById(residentHousehold.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Household not found"));

        List<WaterUsageReadingResponse> allReadings = waterUsageReadingService.listForResident(resident);

        // If the real current calendar month has no readings yet (e.g. no admin has logged one
        // this month), fall back to the most recent month that does have data instead of showing
        // a misleading 0 kL / ₹0 estimate.
        String requestedPrefix = month.toString();
        boolean hasCurrentMonthReading = allReadings.stream()
                .anyMatch(reading -> requestedPrefix.equals(reading.consumptionMonth()));
        String monthPrefix = hasCurrentMonthReading ? requestedPrefix : latestMonthWithReadings(allReadings).orElse(requestedPrefix);

        BigDecimal consumption = allReadings.stream()
                .filter(reading -> monthPrefix.equals(reading.consumptionMonth()))
                .map(WaterUsageReadingResponse::consumption)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        TariffPlanResponse plan = getOrDefault(household.getApartment());
        List<TierChargeLine> breakdown = calculateBreakdown(consumption, plan.tiers());
        BigDecimal total = breakdown.stream().map(TierChargeLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BillEstimateResponse(monthPrefix, consumption, breakdown, total);
    }

    private Optional<String> latestMonthWithReadings(List<WaterUsageReadingResponse> readings) {
        return readings.stream()
                .map(WaterUsageReadingResponse::consumptionMonth)
                .filter(Objects::nonNull)
                .max(String::compareTo);
    }

    private TariffPlanResponse toResponse(TariffPlan plan) {
        List<TariffTierResponse> tiers = plan.getTiers().stream().map(TariffTierResponse::from).toList();
        return new TariffPlanResponse(plan.getId(), tiers, true, plan.getUpdatedAt());
    }
}
