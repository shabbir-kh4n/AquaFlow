package com.shabbir.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminProfileRequest(
        @NotBlank(message = "Full name is required")
        String fullName,

        String phone
) {
}
