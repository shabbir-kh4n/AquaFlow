package com.hydroledger.backend.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.hydroledger.backend.dto.AdminReportResponse;
import com.hydroledger.backend.dto.AdminReportResponse.AdminReportSummary;
import com.hydroledger.backend.dto.AdminReportResponse.HouseholdReportRow;
import com.hydroledger.backend.dto.AdminReportResponse.MonthlyTrendPoint;
import com.hydroledger.backend.exception.ApiException;

/**
 * Renders the community admin's report as a PDF summary, on demand - same PDFBox conventions as
 * {@link InvoicePdfService} (Helvetica Standard-14 fonts, "Rs." instead of the rupee sign since
 * WinAnsi can't encode it, money always at 2dp). Nothing is persisted. The full per-household
 * table is the CSV download's job; this PDF carries the summary, monthly trend and top consumers.
 */
@Service
public class AdminReportPdfService {

    private static final PDFont FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final PDColor RULE_COLOR = new PDColor(new float[] {0.82f}, PDDeviceGray.INSTANCE);
    private static final float GAP_BEFORE_RULE = 6f;
    private static final float GAP_AFTER_RULE = 16f;
    private static final DateTimeFormatter GENERATED_AT_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final float[] TABLE_COLUMNS = {MARGIN, MARGIN + 130, MARGIN + 260, MARGIN + 380};

    public byte[] generate(AdminReportResponse report) {
        try (PDDocument document = new PDDocument()) {
            Writer w = new Writer(document);
            try {
                AdminReportSummary s = report.summary();

                w.centered(FONT_BOLD, 20, safe(report.apartmentName()));
                w.y -= 4;
                w.centered(FONT_REGULAR, 13, "Community Report - " + report.range().from() + " to " + report.range().to());
                w.centered(FONT_REGULAR, 10, "Generated: " + GENERATED_AT_FORMAT.format(LocalDateTime.now()));
                w.rule();

                w.line(FONT_BOLD, 12, "Consumption");
                w.y -= 4;
                w.line(FONT_REGULAR, 11, "Households: " + s.householdCount());
                w.line(FONT_REGULAR, 11, "Total consumption: " + formatKl(s.totalConsumptionKl()) + " kL");
                w.line(FONT_REGULAR, 11, "Average monthly consumption: " + formatKl(s.avgMonthlyConsumptionKl()) + " kL");
                w.rule();

                w.line(FONT_BOLD, 12, "Billing & Collection");
                w.y -= 4;
                w.line(FONT_REGULAR, 11, "Billed: Rs. " + formatCurrency(s.totalBilledAmount()));
                w.line(FONT_REGULAR, 11, "Paid: Rs. " + formatCurrency(s.totalPaidAmount()));
                w.line(FONT_REGULAR, 11, "Outstanding: Rs. " + formatCurrency(s.totalOutstandingAmount()));
                w.line(FONT_REGULAR, 11, "Collection rate: " + formatCurrency(s.collectionRatePercent()) + "%");
                w.line(FONT_REGULAR, 11, "Invoices: " + s.invoiceCount() + " (" + s.unpaidInvoiceCount() + " unpaid)");
                w.line(FONT_REGULAR, 11, "Common-area charges: Rs. " + formatCurrency(s.commonAreaChargesTotal()));
                w.rule();

                w.line(FONT_BOLD, 12, "Bulk Water Purchases");
                w.y -= 4;
                w.line(FONT_REGULAR, 11, "Purchases: " + s.bulkPurchaseCount());
                w.line(FONT_REGULAR, 11, "Volume: " + formatKl(s.bulkPurchaseVolumeKl()) + " kL");
                w.line(FONT_REGULAR, 11, "Cost: Rs. " + formatCurrency(s.bulkPurchaseTotalCost()));
                w.line(FONT_BOLD, 11, "Billed revenue minus bulk cost: Rs. " + formatCurrency(s.billedMinusBulkCost()));
                w.rule();

                w.line(FONT_BOLD, 12, "Monthly Trend");
                w.y -= 6;
                w.row(FONT_BOLD, "Month", "Consumption (kL)", "Billed", "Paid");
                w.ruleTight();
                for (MonthlyTrendPoint point : report.monthlyTrend()) {
                    w.row(FONT_REGULAR, point.periodMonth(), formatKl(point.totalConsumptionKl()),
                            "Rs. " + formatCurrency(point.totalBilledAmount()), "Rs. " + formatCurrency(point.totalPaidAmount()));
                }
                w.rule();

                w.line(FONT_BOLD, 12, "Top Consumers");
                w.y -= 6;
                List<HouseholdReportRow> top = report.topConsumers();
                if (top.isEmpty()) {
                    w.line(FONT_REGULAR, 10, "No consumption recorded in this range.");
                } else {
                    w.row(FONT_BOLD, "House", "Resident", "Consumption (kL)", "Billed");
                    w.ruleTight();
                    for (HouseholdReportRow row : top) {
                        w.row(FONT_REGULAR, safe(row.houseNumber()), truncate(safe(row.residentName()), 110),
                                formatKl(row.totalConsumptionKl()), "Rs. " + formatCurrency(row.billedAmount()));
                    }
                }
            } finally {
                w.close();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate report PDF");
        }
    }

    /** Tracks the current page/content stream and y cursor, starting a new page when space runs out. */
    private static final class Writer {
        private final PDDocument document;
        private PDPageContentStream content;
        float y;

        Writer(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void newPage() throws IOException {
            if (content != null) {
                content.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN;
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN) {
                newPage();
            }
        }

        void line(PDFont font, float size, String text) throws IOException {
            ensureSpace(size + 6);
            text(font, size, MARGIN, y, text);
            y -= size + 6;
        }

        void centered(PDFont font, float size, String text) throws IOException {
            ensureSpace(size + 4);
            float width = font.getStringWidth(text) / 1000f * size;
            text(font, size, (PAGE_WIDTH - width) / 2f, y, text);
            y -= size + 4;
        }

        void row(PDFont font, String c1, String c2, String c3, String c4) throws IOException {
            float size = 10f;
            ensureSpace(size + 6);
            String[] cells = {c1, c2, c3, c4};
            for (int i = 0; i < cells.length; i++) {
                if (cells[i] != null && !cells[i].isEmpty()) {
                    text(font, size, TABLE_COLUMNS[i], y, cells[i]);
                }
            }
            y -= size + 6;
        }

        void rule() throws IOException {
            y -= GAP_BEFORE_RULE;
            drawRule();
            y -= GAP_AFTER_RULE;
        }

        void ruleTight() throws IOException {
            y += 4;
            drawRule();
            y -= 12;
        }

        private void drawRule() throws IOException {
            ensureSpace(GAP_AFTER_RULE);
            content.setStrokingColor(RULE_COLOR);
            content.setLineWidth(0.75f);
            content.moveTo(MARGIN, y);
            content.lineTo(PAGE_WIDTH - MARGIN, y);
            content.stroke();
        }

        private void text(PDFont font, float size, float x, float atY, String value) throws IOException {
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, atY);
            content.showText(value);
            content.endText();
        }

        void close() throws IOException {
            content.close();
        }
    }

    // Standard-14 Helvetica only encodes WinAnsi - replace anything outside it (e.g. a resident
    // name in a non-Latin script) so a single character can't fail the whole PDF.
    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            sb.append((c >= 32 && c <= 126) || (c >= 160 && c <= 255) ? c : '?');
        }
        return sb.toString();
    }

    private static String truncate(String text, float maxWidth) throws IOException {
        float size = 10f;
        if (FONT_REGULAR.getStringWidth(text) / 1000f * size <= maxWidth) {
            return text;
        }
        StringBuilder truncated = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (FONT_REGULAR.getStringWidth(truncated + String.valueOf(c) + "...") / 1000f * size > maxWidth) {
                break;
            }
            truncated.append(c);
        }
        return truncated + "...";
    }

    // Same rule as InvoicePdfService.formatCurrency: money always renders at 2dp.
    private static String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatKl(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
