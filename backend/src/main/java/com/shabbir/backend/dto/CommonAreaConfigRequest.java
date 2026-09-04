package com.shabbir.backend.dto;

import java.util.List;

import com.shabbir.backend.entity.CommonAreaDistributionMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CommonAreaConfigRequest(
        @NotNull(message = "Distribution method is required")
        CommonAreaDistributionMethod distributionMethod,

        @NotEmpty(message = "At least one cost item is required")
        List<@Valid CommonAreaCostItemRequest> costItems
) {
}
