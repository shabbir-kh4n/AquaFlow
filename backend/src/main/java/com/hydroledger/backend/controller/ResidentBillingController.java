package com.hydroledger.backend.controller;

import java.time.YearMonth;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.BillEstimateResponse;
import com.hydroledger.backend.dto.InvoiceResponse;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.service.BillingCycleService;
import com.hydroledger.backend.service.TariffPlanService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/resident")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
public class ResidentBillingController {

    private final TariffPlanService tariffPlanService;
    private final BillingCycleService billingCycleService;

    @GetMapping("/bill-estimate")
    public ResponseEntity<BillEstimateResponse> getBillEstimate(@AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(tariffPlanService.estimateForResident(principal.getUser(), YearMonth.now()));
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<InvoiceResponse>> getInvoices(@AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(billingCycleService.listInvoicesForResident(principal.getUser()));
    }

    @PostMapping("/invoices/{id}/pay")
    public ResponseEntity<InvoiceResponse> payInvoice(@PathVariable Long id,
            @AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(billingCycleService.payInvoiceAsResident(principal.getUser(), id));
    }

    // Simulated checkout only (see RazorpaySimulatorModal) - no real payment gateway.
    @PostMapping("/invoices/pay-all")
    public ResponseEntity<List<InvoiceResponse>> payAllOutstanding(@AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(billingCycleService.payAllOutstandingAsResident(principal.getUser()));
    }

    @GetMapping("/invoices/{id}/pdf")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable Long id,
            @AuthenticationPrincipal ResidentPrincipal principal) {
        byte[] pdf = billingCycleService.generateInvoicePdfForResident(principal.getUser(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + id + ".pdf\"")
                .body(pdf);
    }
}
