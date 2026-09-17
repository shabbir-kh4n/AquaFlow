package com.hydroledger.backend.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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

import com.hydroledger.backend.dto.CommonAreaChargeLine;
import com.hydroledger.backend.dto.InvoiceResponse;
import com.hydroledger.backend.dto.TierChargeLine;
import com.hydroledger.backend.exception.ApiException;

/**
 * Renders a finalized invoice as a one-page PDF, on demand - nothing is persisted, the PDF is
 * built fresh from the same InvoiceResponse data every page/API response already uses.
 */
@Service
public class InvoicePdfService {

    private static final PDFont FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final PDColor RULE_COLOR = new PDColor(new float[] {0.82f}, PDDeviceGray.INSTANCE);
    // Rules read as "closing" whatever text sits right above them, not "introducing" what follows -
    // so the gap above each rule is small (hugs the preceding text) and the gap below is more generous.
    private static final float GAP_BEFORE_RULE = 6f;
    private static final float GAP_AFTER_RULE = 16f;
    // HydroLedger's brand green, used as the "ink" color for the signature stamp so it reads as
    // an authenticity mark distinct from the plain black invoice body text.
    private static final float STAMP_R = 0.184f;
    private static final float STAMP_G = 0.42f;
    private static final float STAMP_B = 0.255f;
    private static final DateTimeFormatter GENERATED_AT_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    public byte[] generate(InvoiceResponse invoice, String apartmentName) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = PDRectangle.A4.getHeight() - MARGIN;

                y = writeCentered(content, FONT_BOLD, 20, y, apartmentName);
                y -= 4;
                y = writeCentered(content, FONT_REGULAR, 13, y, "Water Bill - " + invoice.periodMonth());
                y -= GAP_BEFORE_RULE;
                y = drawRule(content, y);
                y -= GAP_AFTER_RULE;

                y = writeLine(content, FONT_REGULAR, 11, MARGIN, y, "House: " + nullToDash(invoice.houseNumber()));
                y = writeLine(content, FONT_REGULAR, 11, MARGIN, y, "Resident: " + nullToDash(invoice.residentName()));
                y = writeLine(content, FONT_REGULAR, 11, MARGIN, y, "Meter: " + nullToDash(invoice.meterNumber()));
                y = writeLine(content, FONT_REGULAR, 11, MARGIN, y, "Generated: " + formatInstant(invoice));
                y = writeLine(content, FONT_REGULAR, 11, MARGIN, y, "Consumption: " + invoice.consumptionUnits() + " kL");
                y = writeLine(content, FONT_REGULAR, 11, MARGIN, y, "Status: " + (invoice.paid() ? "Paid" : "Unpaid"));
                y -= GAP_BEFORE_RULE;
                y = drawRule(content, y);
                y -= GAP_AFTER_RULE;

                if (invoice.baseAmount() != null && invoice.baseAmount().compareTo(BigDecimal.ZERO) > 0) {
                    y = writeLine(content, FONT_BOLD, 12, MARGIN, y, "Base Charges");
                    y -= 10;
                    y = writeLine(content, FONT_REGULAR, 10, MARGIN, y,
                            invoice.consumptionUnits() + " kL x Rs. " + invoice.basePricePerUnit() + " /kL");
                    y -= 6;
                    y = writeLine(content, FONT_BOLD, 11, MARGIN, y, "Base subtotal: Rs. " + invoice.baseAmount());
                    y -= GAP_BEFORE_RULE;
                    y = drawRule(content, y);
                    y -= GAP_AFTER_RULE;
                }

                y = writeLine(content, FONT_BOLD, 12, MARGIN, y, "Tariff Charges");
                y -= 10;
                y = writeTableHeader(content, y, "Tier", "Units", "Rate/Unit", "Amount");
                y -= GAP_BEFORE_RULE;
                y = drawRule(content, y);
                y -= GAP_AFTER_RULE;
                for (TierChargeLine line : invoice.lineItems()) {
                    y = writeTableRow(content, y,
                            "Tier " + line.tierOrder(),
                            String.valueOf(line.unitsInTier()),
                            "Rs. " + line.ratePerUnit(),
                            "Rs. " + line.amount());
                }
                y -= 6;
                y = writeLine(content, FONT_BOLD, 11, MARGIN, y, "Tariff subtotal: Rs. " + invoice.tariffAmount());
                y -= GAP_BEFORE_RULE;
                y = drawRule(content, y);
                y -= GAP_AFTER_RULE;

                if (!invoice.commonAreaCharges().isEmpty()) {
                    y = writeLine(content, FONT_BOLD, 12, MARGIN, y, "Common Area Charges");
                    y -= 10;
                    y = writeTableHeader(content, y, "Item", "", "", "Amount");
                    y -= GAP_BEFORE_RULE;
                    y = drawRule(content, y);
                    y -= GAP_AFTER_RULE;
                    for (CommonAreaChargeLine line : invoice.commonAreaCharges()) {
                        y = writeTableRow(content, y, line.label(), "", "", "Rs. " + line.amount());
                    }
                    y -= 6;
                    y = writeLine(content, FONT_BOLD, 11, MARGIN, y, "Common area subtotal: Rs. " + invoice.commonAreaAmount());
                    y -= GAP_BEFORE_RULE;
                    y = drawRule(content, y);
                    y -= GAP_AFTER_RULE;
                }

                writeLine(content, FONT_BOLD, 15, MARGIN, y, "Total: Rs. " + invoice.totalAmount());

                drawSignatureStamp(content, invoice, apartmentName);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate invoice PDF");
        }
    }

    private void drawSignatureStamp(PDPageContentStream content, InvoiceResponse invoice, String apartmentName) throws IOException {
        float radius = 62f;
        float cx = PAGE_WIDTH - MARGIN - radius;
        float cy = MARGIN + radius;
        float textMaxWidth = radius * 1.6f;

        drawCircle(content, cx, cy, radius);
        drawCircle(content, cx, cy, radius - 6);

        content.setNonStrokingColor(STAMP_R, STAMP_G, STAMP_B);
        float textY = cy + 20;
        textY = writeCenteredAt(content, FONT_BOLD, 9, cx, textY, "DIGITALLY");
        textY -= 2;
        textY = writeCenteredAt(content, FONT_BOLD, 9, cx, textY, "SIGNED");
        textY -= 6;
        textY = writeCenteredAt(content, FONT_REGULAR, 6.5f, cx, textY,
                truncateToWidth(FONT_REGULAR, 6.5f, apartmentName, textMaxWidth));
        writeCenteredAt(content, FONT_REGULAR, 6.5f, cx, textY, formatInstant(invoice));
        content.setNonStrokingColor(0f, 0f, 0f);
    }

    private void drawCircle(PDPageContentStream content, float cx, float cy, float r) throws IOException {
        // Cubic-bezier circle approximation - the standard "magic number" 0.5522847498 control-point
        // offset makes 4 curves trace a near-perfect circle.
        float k = 0.5522847498f * r;

        content.setStrokingColor(STAMP_R, STAMP_G, STAMP_B);
        content.setLineWidth(1.2f);
        content.moveTo(cx - r, cy);
        content.curveTo(cx - r, cy + k, cx - k, cy + r, cx, cy + r);
        content.curveTo(cx + k, cy + r, cx + r, cy + k, cx + r, cy);
        content.curveTo(cx + r, cy - k, cx + k, cy - r, cx, cy - r);
        content.curveTo(cx - k, cy - r, cx - r, cy - k, cx - r, cy);
        content.closePath();
        content.stroke();
    }

    private float writeCenteredAt(PDPageContentStream content, PDFont font, float fontSize, float centerX, float y, String text) throws IOException {
        float textWidth = font.getStringWidth(text) / 1000f * fontSize;
        float x = centerX - textWidth / 2f;
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
        return y - (fontSize + 4);
    }

    private String truncateToWidth(PDFont font, float fontSize, String text, float maxWidth) throws IOException {
        if (font.getStringWidth(text) / 1000f * fontSize <= maxWidth) {
            return text;
        }
        StringBuilder truncated = new StringBuilder();
        for (char c : text.toCharArray()) {
            String candidate = truncated + String.valueOf(c) + "...";
            if (font.getStringWidth(candidate) / 1000f * fontSize > maxWidth) {
                break;
            }
            truncated.append(c);
        }
        return truncated + "...";
    }

    private String formatInstant(InvoiceResponse invoice) {
        if (invoice.generatedAt() == null) {
            return "-";
        }
        return GENERATED_AT_FORMAT.format(invoice.generatedAt().atZone(ZoneId.systemDefault()));
    }

    private String nullToDash(String value) {
        return value != null ? value : "-";
    }

    private float writeLine(PDPageContentStream content, PDFont font, float fontSize, float x, float y, String text) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
        return y - (fontSize + 6);
    }

    private float writeCentered(PDPageContentStream content, PDFont font, float fontSize, float y, String text) throws IOException {
        float textWidth = font.getStringWidth(text) / 1000f * fontSize;
        float x = (PAGE_WIDTH - textWidth) / 2f;
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
        return y - (fontSize + 4);
    }

    private float drawRule(PDPageContentStream content, float y) throws IOException {
        content.setStrokingColor(RULE_COLOR);
        content.setLineWidth(0.75f);
        content.moveTo(MARGIN, y);
        content.lineTo(PAGE_WIDTH - MARGIN, y);
        content.stroke();
        return y;
    }

    private float writeTableHeader(PDPageContentStream content, float y, String col1, String col2, String col3, String col4) throws IOException {
        return writeRow(content, FONT_BOLD, y, col1, col2, col3, col4);
    }

    private float writeTableRow(PDPageContentStream content, float y, String col1, String col2, String col3, String col4) throws IOException {
        return writeRow(content, FONT_REGULAR, y, col1, col2, col3, col4);
    }

    private float writeRow(PDPageContentStream content, PDFont font, float y, String col1, String col2, String col3, String col4) throws IOException {
        float fontSize = 10f;
        float col1X = MARGIN;
        float col2X = MARGIN + 180;
        float col3X = MARGIN + 280;
        float col4X = PAGE_WIDTH - MARGIN - 80;

        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(col1X, y);
        content.showText(col1);
        content.endText();

        if (!col2.isEmpty()) {
            content.beginText();
            content.setFont(font, fontSize);
            content.newLineAtOffset(col2X, y);
            content.showText(col2);
            content.endText();
        }

        if (!col3.isEmpty()) {
            content.beginText();
            content.setFont(font, fontSize);
            content.newLineAtOffset(col3X, y);
            content.showText(col3);
            content.endText();
        }

        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(col4X, y);
        content.showText(col4);
        content.endText();

        return y - (fontSize + 6);
    }
}
