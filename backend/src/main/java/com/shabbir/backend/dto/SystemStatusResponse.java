package com.shabbir.backend.dto;

public record SystemStatusResponse(
        boolean databaseUp,
        long uptimeMillis,
        String appVersion
) {
}
