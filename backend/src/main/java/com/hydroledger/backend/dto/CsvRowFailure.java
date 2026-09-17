package com.hydroledger.backend.dto;

public record CsvRowFailure(
        int rowNumber,
        String rawLine,
        String reason
) {
}
