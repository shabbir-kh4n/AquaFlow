package com.shabbir.backend.controller;

import java.time.YearMonth;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shabbir.backend.dto.BillEstimateResponse;
import com.shabbir.backend.dto.InvoiceResponse;
import com.shabbir.backend.security.ResidentPrincipal;
import com.shabbir.backend.service.BillingCycleService;
import com.shabbir.backend.service.TariffPlanService;

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
}
