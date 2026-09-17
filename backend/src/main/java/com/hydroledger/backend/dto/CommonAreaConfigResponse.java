package com.hydroledger.backend.dto;

import java.time.Instant;
import java.util.List;

import com.hydroledger.backend.entity.CommonAreaDistributionMethod;

public record CommonAreaConfigResponse(
        Long id,
        CommonAreaDistributionMethod distributionMethod,
        List<CommonAreaCostItemResponse> costItems,
        boolean persisted,
        Instant updatedAt
) {
}
