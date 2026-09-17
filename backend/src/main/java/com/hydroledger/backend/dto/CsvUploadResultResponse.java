package com.hydroledger.backend.dto;

import java.util.List;

public record CsvUploadResultResponse(
        int totalRows,
        int successCount,
        int failureCount,
        List<CsvRowFailure> failures
) {
}
