package com.hydroledger.backend.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import com.hydroledger.backend.dto.AdminReportResponse;
import com.hydroledger.backend.dto.AdminReportResponse.AdminReportSummary;
import com.hydroledger.backend.dto.AdminReportResponse.HouseholdReportRow;
import com.hydroledger.backend.dto.AdminReportResponse.MonthlyTrendPoint;
import com.hydroledger.backend.dto.AdminReportResponse.ReportRangeInfo;
import com.hydroledger.backend.service.AdminReportPdfService;

class AdminReportPdfServiceTests {

    private final AdminReportPdfService pdfService = new AdminReportPdfService();

    private AdminReportResponse report(List<MonthlyTrendPoint> trend, String residentName) {
        AdminReportSummary summary = new AdminReportSummary(2, new BigDecimal("35.000"), new BigDecimal("11.667"),
                new BigDecimal("600.456"), new BigDecimal("300.1"), new BigDecimal("300.356"), new BigDecimal("50.00"),
                3, 2, new BigDecimal("45.5"), 1, new BigDecimal("240"), new BigDecimal("12.000"), new BigDecimal("360.456"));
        HouseholdReportRow row = new HouseholdReportRow(1L, "A-1", residentName, "MTR-1", new BigDecimal("30"),
                new BigDecimal("10"), new BigDecimal("500"), new BigDecimal("300"), new BigDecimal("200"), 2, 1);
        return new AdminReportResponse("Sunrise Towers",
                new ReportRangeInfo("custom", "2026-01", "2026-03", List.of("2026-01", "2026-02", "2026-03")),
                summary, List.of(row), List.of(row), trend);
    }

    private String extractText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void generatesPdfWithHeaderAndTwoDecimalCurrency() throws IOException {
        byte[] pdf = pdfService.generate(report(List.of(
                new MonthlyTrendPoint("2026-01", new BigDecimal("10"), new BigDecimal("400.005"), new BigDecimal("300"), BigDecimal.ZERO)),
                "Asha"));

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        String text = extractText(pdf);
        assertThat(text).contains("Sunrise Towers");
        assertThat(text).contains("Community Report - 2026-01 to 2026-03");
        assertThat(text).contains("Billed: Rs. 600.46");
        assertThat(text).contains("Paid: Rs. 300.10");
        assertThat(text).contains("Collection rate: 50.00%");
        assertThat(text).contains("Common-area charges: Rs. 45.50");
        assertThat(text).contains("Cost: Rs. 240.00");
        assertThat(text).contains("Rs. 400.01");
        assertThat(text).doesNotContain("₹");
    }

    @Test
    void spillsOntoExtraPagesForLongRangesAndSurvivesNonLatinNames() throws IOException {
        List<MonthlyTrendPoint> trend = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            trend.add(new MonthlyTrendPoint("20" + (20 + i / 12) + "-" + String.format("%02d", i % 12 + 1),
                    BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO));
        }

        byte[] pdf = pdfService.generate(report(trend, "आशा"));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
    }
}
