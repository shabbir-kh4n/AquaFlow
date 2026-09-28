package com.hydroledger.backend.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import com.hydroledger.backend.dto.InvoiceResponse;
import com.hydroledger.backend.service.StatementPdfService;

class StatementPdfServiceTests {

    private final StatementPdfService statementPdfService = new StatementPdfService();

    private InvoiceResponse invoice(long id, String period, String amount, boolean paid) {
        return new InvoiceResponse(id, 1L, "A-101", "Jane Resident", "MTR-0001", "ACTIVE", new BigDecimal("1000"), period,
                new BigDecimal("12.345"), null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(amount), new BigDecimal(amount), BigDecimal.ZERO,
                Instant.now(), paid, paid ? Instant.now() : null, List.of(), List.of());
    }

    private String text(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void generatesValidPdfWithTwoDecimalAmountsAndTotals() throws IOException {
        byte[] pdf = statementPdfService.generate("Sunrise Towers", "A-101", "Jane Resident", List.of(
                invoice(1L, "2026-07", "100.5", true),
                // More than 2dp (can happen after tier multiplication) must still render at 2dp.
                invoice(2L, "2026-08", "49.999", false)));

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String text = text(pdf);
        assertThat(text).contains("Account Statement", "Sunrise Towers", "A-101", "Jane Resident");
        assertThat(text).contains("Rs. 100.50", "Rs. 50.00");
        assertThat(text).contains("Total billed: Rs. 150.50", "Total paid: Rs. 100.50", "Outstanding: Rs. 50.00");
        assertThat(text).contains("2026-07", "2026-08", "Paid", "Unpaid");
        assertThat(text).doesNotContain("₹");
    }

    @Test
    void generatesPdfWithNoInvoices() throws IOException {
        byte[] pdf = statementPdfService.generate("Sunrise Towers", "A-101", "Jane Resident", List.of());

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(text(pdf)).contains("No invoices yet.", "Outstanding: Rs. 0.00");
    }

    @Test
    void flowsOntoExtraPagesForLongHistories() throws IOException {
        List<InvoiceResponse> invoices = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            invoices.add(invoice(i, String.format("20%02d-01", i), "10.00", true));
        }

        byte[] pdf = statementPdfService.generate("Sunrise Towers", "A-101", "Jane Resident", invoices);

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
        }
    }

    @Test
    void replacesCharactersTheStandardFontCannotEncode() throws IOException {
        byte[] pdf = statementPdfService.generate("सूर्य Towers", "A-101", "Jane", List.of());

        assertThat(text(pdf)).contains("Towers");
    }
}
