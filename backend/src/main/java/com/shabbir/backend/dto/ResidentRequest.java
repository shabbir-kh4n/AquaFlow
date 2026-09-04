package com.shabbir.backend.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResidentRequest(
        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Phone number is required")
        String phone,

        @NotBlank(message = "House / unit number is required")
        String houseNumber,

        @NotBlank(message = "Meter number is required")
        @Pattern(regexp = "^MTR-\\d{4}$", message = "Meter number must look like MTR-1234")
        String meterNumber,

        @NotBlank(message = "Government ID is required")
        @Pattern(regexp = "^99\\d{4}$", message = "Government ID must be 6 digits starting with 99")
        String governmentId,

        @NotBlank(message = "Gender is required")
        String gender,

        @NotBlank(message = "Date of birth is required")
        String dateOfBirth,

        @NotBlank(message = "Date of joining is required")
        String dateOfJoining,

        boolean bypassSetPassword,

        String password,

        @DecimalMin(value = "0.0", message = "Flat size cannot be negative")
        BigDecimal flatSizeSqft
) {
}
