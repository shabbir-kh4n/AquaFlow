package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record ResidentReportResponse(
        ReportRange range,
        Summary summary,
        List<MonthRow> months
) {
    public record ReportRange(String key, String from, String to, String previousFrom, String previousTo) {
    }

    public record Summary(
            BigDecimal totalConsumptionKl,
            BigDecimal totalBilledAmount,
            BigDecimal totalPaidAmount,
            BigDecimal outstandingAmount,
            // Averaged over the months in range that have recorded consumption, not every calendar
            // month, so a 12-month range with only 3 months of readings isn't dragged towards zero.
            BigDecimal avgMonthlyConsumptionKl,
            BigDecimal previousPeriodConsumptionKl,
            // Null when the previous equal-length period had no consumption to compare against.
            BigDecimal consumptionChangePercent
    ) {
    }

    public record MonthRow(
            String periodMonth,
            BigDecimal consumptionKl,
            BigDecimal billedAmount,
            BigDecimal paidAmount,
            boolean invoiced,
            boolean paid
    ) {
    }
}
