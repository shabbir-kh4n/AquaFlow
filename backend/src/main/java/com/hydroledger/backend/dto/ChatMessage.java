package com.hydroledger.backend.dto;

public record ChatMessage(
        String role,
        String content
) {
}
