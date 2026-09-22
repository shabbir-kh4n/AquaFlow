package com.hydroledger.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record PushUnsubscribeRequest(
        @NotBlank(message = "Endpoint is required")
        String endpoint
) {
}
