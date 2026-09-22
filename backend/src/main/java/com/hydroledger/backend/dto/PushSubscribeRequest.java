package com.hydroledger.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PushSubscribeRequest(
        @NotBlank(message = "Endpoint is required")
        String endpoint,

        @NotNull(message = "Keys are required")
        @Valid
        Keys keys
) {
    public record Keys(
            @NotBlank(message = "p256dh key is required")
            String p256dh,

            @NotBlank(message = "auth key is required")
            String auth
    ) {
    }
}
