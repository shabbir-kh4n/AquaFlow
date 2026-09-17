package com.hydroledger.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hydroledger.backend.dto.CommonAreaChargeLine;
import com.hydroledger.backend.dto.CommonAreaConfigRequest;
import com.hydroledger.backend.dto.CommonAreaConfigResponse;
import com.hydroledger.backend.dto.CommonAreaCostItemResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommonAreaConfig;
import com.hydroledger.backend.entity.CommonAreaCostItem;
import com.hydroledger.backend.entity.CommonAreaDistributionMethod;
import com.hydroledger.backend.repository.CommonAreaConfigRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommonAreaConfigService {

    private final CommonAreaConfigRepository commonAreaConfigRepository;

    public CommonAreaConfigResponse getOrDefault(Apartment apartment) {
        return commonAreaConfigRepository.findByApartment(apartment)
                .map(this::toResponse)
                .orElseGet(() -> new CommonAreaConfigResponse(null, CommonAreaDistributionMethod.CONSUMPTION, List.of(), false, null));
    }

    @Transactional
    public CommonAreaConfigResponse save(Apartment apartment, CommonAreaConfigRequest request) {
        CommonAreaConfig config = commonAreaConfigRepository.findByApartment(apartment)
                .orElseGet(() -> CommonAreaConfig.builder().apartment(apartment).build());
        config.setDistributionMethod(request.distributionMethod());

        // Same clear-then-flush-before-reinsert precaution as TariffPlanService.save: avoids any
        // insert-before-delete flush-ordering hazard, even though cost items have no unique-order
        // constraint of their own to collide with.
        config.getCostItems().clear();
        commonAreaConfigRepository.saveAndFlush(config);

        for (var item : request.costItems()) {
            config.getCostItems().add(CommonAreaCostItem.builder()
                    .config(config)
                    .label(item.label())
                    .monthlyAmount(item.monthlyAmount())
                    .active(item.active())
                    .build());
        }

        return toResponse(commonAreaConfigRepository.save(config));
    }

    /**
     * Splits each active cost item's monthly amount across the given households, weighted by
     * consumption or floor area depending on the configured distribution method. Falls back to
     * floor area if consumption totals to zero, then to an equal split if neither is usable, so a
     * configured charge is never silently dropped for a cycle with no usable weighting data.
     */
    public Map<Long, List<CommonAreaChargeLine>> apportion(CommonAreaConfigResponse config,
            Map<Long, BigDecimal> consumptionByHouseholdId, Map<Long, BigDecimal> sqftByHouseholdId) {

        List<CommonAreaCostItemResponse> activeItems = config.costItems().stream()
                .filter(CommonAreaCostItemResponse::active)
                .toList();
        if (activeItems.isEmpty() || consumptionByHouseholdId.isEmpty()) {
            return Map.of();
        }

        Map<Long, BigDecimal> weights = resolveWeights(config.distributionMethod(), consumptionByHouseholdId, sqftByHouseholdId);
        BigDecimal totalWeight = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return Map.of();
        }

        Map<Long, List<CommonAreaChargeLine>> result = new LinkedHashMap<>();
        List<Long> householdIds = new ArrayList<>(weights.keySet());

        for (CommonAreaCostItemResponse item : activeItems) {
            BigDecimal allocated = BigDecimal.ZERO;
            for (int i = 0; i < householdIds.size(); i++) {
                Long householdId = householdIds.get(i);
                BigDecimal share;
                if (i == householdIds.size() - 1) {
                    // last household absorbs the rounding remainder so per-item totals reconcile exactly
                    share = item.monthlyAmount().subtract(allocated);
                } else {
                    share = item.monthlyAmount()
                            .multiply(weights.get(householdId))
                            .divide(totalWeight, 2, RoundingMode.HALF_UP);
                    allocated = allocated.add(share);
                }
                result.computeIfAbsent(householdId, id -> new ArrayList<>())
                        .add(new CommonAreaChargeLine(item.id(), item.label(), share));
            }
        }

        return result;
    }

    private Map<Long, BigDecimal> resolveWeights(CommonAreaDistributionMethod method,
            Map<Long, BigDecimal> consumption, Map<Long, BigDecimal> sqft) {
        boolean useConsumption = method == CommonAreaDistributionMethod.CONSUMPTION && isUsable(consumption);
        if (useConsumption) {
            return consumption;
        }

        if (isUsable(sqft)) {
            return sqft;
        }

        // Last resort: nobody has consumption or floor area recorded — split equally rather than
        // silently dropping the common-area charge for the whole cycle.
        return consumption.keySet().stream()
                .collect(java.util.stream.Collectors.toMap(id -> id, id -> BigDecimal.ONE, (a, b) -> a, LinkedHashMap::new));
    }

    private boolean isUsable(Map<Long, BigDecimal> weights) {
        return weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.ZERO) > 0;
    }

    private CommonAreaConfigResponse toResponse(CommonAreaConfig config) {
        List<CommonAreaCostItemResponse> items = config.getCostItems().stream()
                .map(CommonAreaCostItemResponse::from)
                .toList();
        return new CommonAreaConfigResponse(config.getId(), config.getDistributionMethod(), items, true, config.getUpdatedAt());
    }
}
