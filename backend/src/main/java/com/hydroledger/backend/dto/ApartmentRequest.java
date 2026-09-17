package com.hydroledger.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ApartmentRequest(
        @NotBlank(message = "Community name is required")
        String name,

        String address,

        String mainsSource
) {
}
