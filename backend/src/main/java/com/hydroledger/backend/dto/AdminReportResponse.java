package com.hydroledger.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminReportResponse(
        String apartmentName,
        ReportRangeInfo range,
        AdminReportSummary summary,
        List<HouseholdReportRow> households,
        List<HouseholdReportRow> topConsumers,
        List<MonthlyTrendPoint> monthlyTrend
) {
    public record ReportRangeInfo(String key, String from, String to, List<String> months) {
    }

    public record AdminReportSummary(
            int householdCount,
            BigDecimal totalConsumptionKl,
            BigDecimal avgMonthlyConsumptionKl,
            BigDecimal totalBilledAmount,
            BigDecimal totalPaidAmount,
            BigDecimal totalOutstandingAmount,
            // Paid / billed x 100, 2dp; 0 when nothing was billed in the range.
            BigDecimal collectionRatePercent,
            int invoiceCount,
            int unpaidInvoiceCount,
            BigDecimal commonAreaChargesTotal,
            int bulkPurchaseCount,
            BigDecimal bulkPurchaseTotalCost,
            BigDecimal bulkPurchaseVolumeKl,
            // Billed revenue minus bulk water purchase cost for the same range.
            BigDecimal billedMinusBulkCost
    ) {
    }

    public record HouseholdReportRow(
            Long householdId,
            String houseNumber,
            String residentName,
            String meterNumber,
            BigDecimal totalConsumptionKl,
            BigDecimal avgMonthlyConsumptionKl,
            BigDecimal billedAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            int invoiceCount,
            int unpaidInvoiceCount
    ) {
    }

    public record MonthlyTrendPoint(
            String periodMonth,
            BigDecimal totalConsumptionKl,
            BigDecimal totalBilledAmount,
            BigDecimal totalPaidAmount,
            BigDecimal bulkPurchaseCost
    ) {
    }
}
