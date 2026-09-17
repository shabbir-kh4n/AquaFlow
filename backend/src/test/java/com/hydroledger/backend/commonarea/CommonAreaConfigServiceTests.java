package com.hydroledger.backend.commonarea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hydroledger.backend.dto.CommonAreaChargeLine;
import com.hydroledger.backend.dto.CommonAreaConfigRequest;
import com.hydroledger.backend.dto.CommonAreaConfigResponse;
import com.hydroledger.backend.dto.CommonAreaCostItemRequest;
import com.hydroledger.backend.dto.CommonAreaCostItemResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommonAreaConfig;
import com.hydroledger.backend.entity.CommonAreaDistributionMethod;
import com.hydroledger.backend.repository.CommonAreaConfigRepository;
import com.hydroledger.backend.service.CommonAreaConfigService;

@ExtendWith(MockitoExtension.class)
class CommonAreaConfigServiceTests {

    @Mock
    private CommonAreaConfigRepository commonAreaConfigRepository;

    @InjectMocks
    private CommonAreaConfigService commonAreaConfigService;

    private Apartment apartment;

    @BeforeEach
    void setUp() {
        apartment = Apartment.builder().id(1L).name("Sunrise Towers").build();
    }

    @Test
    void getOrDefaultReturnsUnpersistedDefaultWhenNoneSaved() {
        when(commonAreaConfigRepository.findByApartment(apartment)).thenReturn(Optional.empty());

        CommonAreaConfigResponse response = commonAreaConfigService.getOrDefault(apartment);

        assertThat(response.persisted()).isFalse();
        assertThat(response.distributionMethod()).isEqualTo(CommonAreaDistributionMethod.CONSUMPTION);
        assertThat(response.costItems()).isEmpty();
    }

    @Test
    void saveClearsAndReinsertsCostItems() {
        CommonAreaConfig config = CommonAreaConfig.builder().id(1L).apartment(apartment).build();
        when(commonAreaConfigRepository.findByApartment(apartment)).thenReturn(Optional.of(config));
        when(commonAreaConfigRepository.saveAndFlush(config)).thenReturn(config);
        when(commonAreaConfigRepository.save(config)).thenReturn(config);

        CommonAreaConfigRequest request = new CommonAreaConfigRequest(CommonAreaDistributionMethod.FLOOR_AREA,
                List.of(new CommonAreaCostItemRequest("Pool Maintenance", new BigDecimal("22000"), true)));

        CommonAreaConfigResponse response = commonAreaConfigService.save(apartment, request);

        assertThat(response.distributionMethod()).isEqualTo(CommonAreaDistributionMethod.FLOOR_AREA);
        assertThat(response.persisted()).isTrue();
        assertThat(config.getCostItems()).hasSize(1);
        assertThat(config.getCostItems().get(0).getLabel()).isEqualTo("Pool Maintenance");
    }

    @Test
    void apportionReturnsEmptyWhenNoActiveCostItems() {
        CommonAreaConfigResponse config = new CommonAreaConfigResponse(1L, CommonAreaDistributionMethod.CONSUMPTION,
                List.of(new CommonAreaCostItemResponse(1L, "Pool", new BigDecimal("1000"), false)), true, null);

        Map<Long, List<CommonAreaChargeLine>> result = commonAreaConfigService.apportion(config,
                Map.of(1L, new BigDecimal("10")), Map.of(1L, BigDecimal.ZERO));

        assertThat(result).isEmpty();
    }

    @Test
    void apportionSplitsByConsumptionProportionally() {
        CommonAreaConfigResponse config = new CommonAreaConfigResponse(1L, CommonAreaDistributionMethod.CONSUMPTION,
                List.of(new CommonAreaCostItemResponse(1L, "Pool Maintenance", new BigDecimal("300.00"), true)), true, null);

        Map<Long, BigDecimal> consumption = new java.util.LinkedHashMap<>();
        consumption.put(1L, new BigDecimal("10"));
        consumption.put(2L, new BigDecimal("20"));
        Map<Long, BigDecimal> sqft = Map.of(1L, BigDecimal.ZERO, 2L, BigDecimal.ZERO);

        Map<Long, List<CommonAreaChargeLine>> result = commonAreaConfigService.apportion(config, consumption, sqft);

        BigDecimal household1Amount = result.get(1L).get(0).amount();
        BigDecimal household2Amount = result.get(2L).get(0).amount();

        assertThat(household1Amount).isEqualByComparingTo("100.00");
        assertThat(household2Amount).isEqualByComparingTo("200.00");
        assertThat(household1Amount.add(household2Amount)).isEqualByComparingTo("300.00");
    }

    @Test
    void apportionFallsBackToFloorAreaWhenConsumptionIsAllZero() {
        CommonAreaConfigResponse config = new CommonAreaConfigResponse(1L, CommonAreaDistributionMethod.CONSUMPTION,
                List.of(new CommonAreaCostItemResponse(1L, "Pool Maintenance", new BigDecimal("300.00"), true)), true, null);

        Map<Long, BigDecimal> consumption = new java.util.LinkedHashMap<>();
        consumption.put(1L, BigDecimal.ZERO);
        consumption.put(2L, BigDecimal.ZERO);
        Map<Long, BigDecimal> sqft = new java.util.LinkedHashMap<>();
        sqft.put(1L, new BigDecimal("1000"));
        sqft.put(2L, new BigDecimal("2000"));

        Map<Long, List<CommonAreaChargeLine>> result = commonAreaConfigService.apportion(config, consumption, sqft);

        assertThat(result.get(1L).get(0).amount()).isEqualByComparingTo("100.00");
        assertThat(result.get(2L).get(0).amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void apportionFallsBackToEqualSplitWhenNeitherConsumptionNorFloorAreaUsable() {
        CommonAreaConfigResponse config = new CommonAreaConfigResponse(1L, CommonAreaDistributionMethod.CONSUMPTION,
                List.of(new CommonAreaCostItemResponse(1L, "Pool Maintenance", new BigDecimal("300.00"), true)), true, null);

        Map<Long, BigDecimal> consumption = new java.util.LinkedHashMap<>();
        consumption.put(1L, BigDecimal.ZERO);
        consumption.put(2L, BigDecimal.ZERO);
        Map<Long, BigDecimal> sqft = new java.util.LinkedHashMap<>();
        sqft.put(1L, BigDecimal.ZERO);
        sqft.put(2L, BigDecimal.ZERO);

        Map<Long, List<CommonAreaChargeLine>> result = commonAreaConfigService.apportion(config, consumption, sqft);

        assertThat(result.get(1L).get(0).amount()).isEqualByComparingTo("150.00");
        assertThat(result.get(2L).get(0).amount()).isEqualByComparingTo("150.00");
    }

    @Test
    void apportionReturnsEmptyWhenNoBillableHouseholds() {
        CommonAreaConfigResponse config = new CommonAreaConfigResponse(1L, CommonAreaDistributionMethod.CONSUMPTION,
                List.of(new CommonAreaCostItemResponse(1L, "Pool", new BigDecimal("300.00"), true)), true, null);

        Map<Long, List<CommonAreaChargeLine>> result = commonAreaConfigService.apportion(config, Map.of(), Map.of());

        assertThat(result).isEmpty();
    }
}
