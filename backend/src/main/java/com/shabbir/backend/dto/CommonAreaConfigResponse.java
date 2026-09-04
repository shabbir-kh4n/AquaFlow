package com.shabbir.backend.dto;

import java.time.Instant;
import java.util.List;

import com.shabbir.backend.entity.CommonAreaDistributionMethod;

public record CommonAreaConfigResponse(
        Long id,
        CommonAreaDistributionMethod distributionMethod,
        List<CommonAreaCostItemResponse> costItems,
        boolean persisted,
        Instant updatedAt
) {
}
