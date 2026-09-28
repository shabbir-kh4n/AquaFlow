package com.hydroledger.backend.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
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

import com.hydroledger.backend.dto.InvoiceResponse;
import com.hydroledger.backend.exception.ApiException;

/**
 * Renders a resident's account statement (every invoice of their household + billed/paid/outstanding
 * totals) as a PDF, on demand - nothing is persisted. Same PDFBox conventions as InvoicePdfService:
 * standard-14 Helvetica, "Rs." instead of the rupee sign (WinAnsi can't encode it), money always 2dp.
 * Flows onto extra pages when the invoice table doesn't fit on one.
 */
@Service
public class StatementPdfService {

    private static final PDFont FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final PDColor RULE_COLOR = new PDColor(new float[] {0.82f}, PDDeviceGray.INSTANCE);
    private static final float TABLE_FONT_SIZE = 10f;
    private static final float ROW_HEIGHT = TABLE_FONT_SIZE + 6;
    // Column x offsets: Period, Consumption, Amount, Status, Paid on.
    private static final float[] COLUMNS = {MARGIN, MARGIN + 90, MARGIN + 200, MARGIN + 310, MARGIN + 390};
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter GENERATED_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    public byte[] generate(String apartmentName, String houseNumber, String residentName, List<InvoiceResponse> invoices) {
        BigDecimal totalBilled = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (InvoiceResponse invoice : invoices) {
            BigDecimal amount = invoice.totalAmount() != null ? invoice.totalAmount() : BigDecimal.ZERO;
            totalBilled = totalBilled.add(amount);
            if (invoice.paid()) {
                totalPaid = totalPaid.add(amount);
            }
        }
        BigDecimal outstanding = totalBilled.subtract(totalPaid);

        try (PDDocument document = new PDDocument()) {
            PageCursor cursor = new PageCursor(document);
            try {
                cursor.writeCentered(FONT_BOLD, 20, nullToDash(apartmentName));
                cursor.y -= 4;
                cursor.writeCentered(FONT_REGULAR, 13, "Account Statement");
                cursor.rule();

                cursor.writeLine(FONT_REGULAR, 11, "House: " + nullToDash(houseNumber));
                cursor.writeLine(FONT_REGULAR, 11, "Resident: " + nullToDash(residentName));
                cursor.writeLine(FONT_REGULAR, 11, "Generated: "
                        + GENERATED_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault())));
                cursor.rule();

                cursor.writeLine(FONT_BOLD, 11, "Total billed: Rs. " + formatCurrency(totalBilled));
                cursor.writeLine(FONT_BOLD, 11, "Total paid: Rs. " + formatCurrency(totalPaid));
                cursor.writeLine(FONT_BOLD, 11, "Outstanding: Rs. " + formatCurrency(outstanding));
                cursor.rule();

                cursor.writeLine(FONT_BOLD, 12, "Invoices");
                cursor.y -= 4;
                if (invoices.isEmpty()) {
                    cursor.writeLine(FONT_REGULAR, 10, "No invoices yet.");
                } else {
                    cursor.tableHeader();
                    for (InvoiceResponse invoice : invoices) {
                        if (cursor.y < MARGIN + ROW_HEIGHT) {
                            cursor.newPage();
                            cursor.tableHeader();
                        }
                        cursor.row(FONT_REGULAR,
                                nullToDash(invoice.periodMonth()),
                                formatUnits(invoice.consumptionUnits()) + " kL",
                                "Rs. " + formatCurrency(invoice.totalAmount()),
                                invoice.paid() ? "Paid" : "Unpaid",
                                formatDate(invoice.paidAt()));
                    }
                }
            } finally {
                cursor.close();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate statement PDF");
        }
    }

    // Always render money at 2dp regardless of the underlying BigDecimal's scale (same as InvoicePdfService).
    static String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatUnits(BigDecimal units) {
        if (units == null) {
            return "0";
        }
        return units.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String formatDate(Instant instant) {
        return instant == null ? "-" : DATE_FORMAT.format(instant.atZone(ZoneId.systemDefault()));
    }

    private static String nullToDash(String value) {
        return value != null && !value.isBlank() ? value : "-";
    }

    /** Tracks the current page/content stream and y position, starting a new page on demand. */
    private static final class PageCursor {
        private final PDDocument document;
        private PDPageContentStream content;
        private float y;

        PageCursor(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        void newPage() throws IOException {
            if (content != null) {
                content.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN;
        }

        void close() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
        }

        void writeLine(PDFont font, float fontSize, String text) throws IOException {
            text(font, fontSize, MARGIN, y, text);
            y -= fontSize + 6;
        }

        void writeCentered(PDFont font, float fontSize, String text) throws IOException {
            String safe = encodable(font, text);
            float width = font.getStringWidth(safe) / 1000f * fontSize;
            text(font, fontSize, (PAGE_WIDTH - width) / 2f, y, safe);
            y -= fontSize + 4;
        }

        void rule() throws IOException {
            y -= 6;
            content.setStrokingColor(RULE_COLOR);
            content.setLineWidth(0.75f);
            content.moveTo(MARGIN, y);
            content.lineTo(PAGE_WIDTH - MARGIN, y);
            content.stroke();
            y -= 16;
        }

        void tableHeader() throws IOException {
            row(FONT_BOLD, "Period", "Consumption", "Amount", "Status", "Paid on");
            rule();
        }

        void row(PDFont font, String... cells) throws IOException {
            for (int i = 0; i < cells.length && i < COLUMNS.length; i++) {
                text(font, TABLE_FONT_SIZE, COLUMNS[i], y, cells[i]);
            }
            y -= ROW_HEIGHT;
        }

        private void text(PDFont font, float fontSize, float x, float atY, String value) throws IOException {
            content.beginText();
            content.setFont(font, fontSize);
            content.newLineAtOffset(x, atY);
            content.showText(encodable(font, value));
            content.endText();
        }

        // Standard-14 fonts only cover WinAnsi; a resident/apartment name in another script would
        // otherwise make showText throw. Swap any unencodable character for '?' instead of failing.
        private static String encodable(PDFont font, String value) {
            StringBuilder out = new StringBuilder(value.length());
            value.codePoints().forEach(cp -> {
                String ch = new String(Character.toChars(cp));
                try {
                    font.encode(ch);
                    out.append(ch);
                } catch (IllegalArgumentException | IOException ex) {
                    out.append('?');
                }
            });
            return out.toString();
        }
    }
}
