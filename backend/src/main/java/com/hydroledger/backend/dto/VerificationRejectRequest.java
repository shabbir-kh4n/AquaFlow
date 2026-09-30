package com.hydroledger.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerificationRejectRequest(
        @NotBlank(message = "A reason for rejection is required")
        @Size(max = 1000, message = "Reason must be at most 1000 characters")
        String reason
) {
}
