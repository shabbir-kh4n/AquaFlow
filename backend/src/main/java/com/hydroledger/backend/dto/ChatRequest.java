package com.hydroledger.backend.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String message,
        List<ChatMessage> history,
        // Optional ISO 639 code of the user's selected UI language; replies default to English.
        String language
) {
}
