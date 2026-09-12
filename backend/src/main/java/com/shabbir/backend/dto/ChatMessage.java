package com.shabbir.backend.dto;

public record ChatMessage(
        String role,
        String content
) {
}
