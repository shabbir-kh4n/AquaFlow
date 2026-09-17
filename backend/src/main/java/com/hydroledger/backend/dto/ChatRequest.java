package com.hydroledger.backend.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String message,
        List<ChatMessage> history
) {
}
