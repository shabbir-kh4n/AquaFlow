package com.hydroledger.backend.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hydroledger.backend.dto.CommonAreaChargeLine;
import com.hydroledger.backend.dto.InvoiceResponse;
import com.hydroledger.backend.dto.TierChargeLine;
import com.hydroledger.backend.service.InvoicePdfService;

class InvoicePdfServiceTests {

    private final InvoicePdfService invoicePdfService = new InvoicePdfService();

    @Test
    void generateProducesNonEmptyPdfBytes() {
        InvoiceResponse invoice = new InvoiceResponse(
                5L, 1L, "A-101", "Jane Resident", "MTR-0001", "2026-09",
                new BigDecimal("25"), new BigDecimal("190.00"), new BigDecimal("190.00"), BigDecimal.ZERO,
                Instant.now(), false, null,
                List.of(new TierChargeLine(1, new BigDecimal("10"), new BigDecimal("5.00"), new BigDecimal("50.00"))),
                List.of());

        byte[] pdf = invoicePdfService.generate(invoice, "Sunrise Towers");

        assertThat(pdf).isNotEmpty();
        // A PDF file always starts with the "%PDF-" magic bytes.
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void generateHandlesCommonAreaChargesAndPaidInvoice() {
        InvoiceResponse invoice = new InvoiceResponse(
                5L, 1L, "A-101", "Jane Resident", "MTR-0001", "2026-09",
                new BigDecimal("25"), new BigDecimal("290.00"), new BigDecimal("190.00"), new BigDecimal("100.00"),
                Instant.now(), true, Instant.now(),
                List.of(new TierChargeLine(1, new BigDecimal("10"), new BigDecimal("5.00"), new BigDecimal("50.00"))),
                List.of(new CommonAreaChargeLine(1L, "Pool Maintenance", new BigDecimal("100.00"))));

        byte[] pdf = invoicePdfService.generate(invoice, "Sunrise Towers");

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void generateHandlesNullOptionalFieldsGracefully() {
        InvoiceResponse invoice = new InvoiceResponse(
                5L, 1L, null, null, null, "2026-09",
                new BigDecimal("25"), new BigDecimal("190.00"), new BigDecimal("190.00"), BigDecimal.ZERO,
                null, false, null,
                List.of(), List.of());

        byte[] pdf = invoicePdfService.generate(invoice, "Sunrise Towers");

        assertThat(pdf).isNotEmpty();
    }
}
