package com.shabbir.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlatformReportResponse(
        ReportRangeInfo range,
        PlatformReportSummary summary,
        List<CommunityReportRow> communities,
        List<MonthlyTrendPoint> monthlyTrend
) {
    public record ReportRangeInfo(String key, String from, String to, List<String> months) {
    }

    public record PlatformReportSummary(
            int totalCommunities,
            int totalResidents,
            BigDecimal totalConsumptionKl,
            BigDecimal totalBilledAmount,
            BigDecimal avgConsumptionPerResident,
            BigDecimal avgBilledPerResident
    ) {
    }

    public record CommunityReportRow(
            Long adminId,
            Long apartmentId,
            String apartmentName,
            String adminFullName,
            int residentCount,
            BigDecimal totalConsumptionKl,
            BigDecimal totalBilledAmount,
            BigDecimal avgConsumptionPerResident,
            BigDecimal avgBilledPerResident
    ) {
    }

    public record MonthlyTrendPoint(String periodMonth, BigDecimal totalConsumptionKl, BigDecimal totalBilledAmount) {
    }
}
