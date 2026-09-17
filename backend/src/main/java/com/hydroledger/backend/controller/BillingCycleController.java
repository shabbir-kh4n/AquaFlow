package com.hydroledger.backend.controller;

import java.time.YearMonth;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.BillingCycleDetailResponse;
import com.hydroledger.backend.dto.BillingCycleOpenRequest;
import com.hydroledger.backend.dto.BillingCycleResponse;
import com.hydroledger.backend.dto.InvoicePaymentStatusRequest;
import com.hydroledger.backend.dto.InvoiceResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.ApartmentService;
import com.hydroledger.backend.service.BillingCycleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/billing-cycles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BillingCycleController {

    private final BillingCycleService billingCycleService;
    private final ApartmentService apartmentService;

    @PostMapping
    public ResponseEntity<BillingCycleResponse> open(@Valid @RequestBody BillingCycleOpenRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(billingCycleService.open(apartment, YearMonth.parse(request.periodMonth())));
    }

    @GetMapping
    public ResponseEntity<List<BillingCycleResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(billingCycleService.list(apartment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BillingCycleDetailResponse> getDetail(@PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(billingCycleService.getDetail(apartment, id));
    }

    @PostMapping("/{id}/finalize")
    public ResponseEntity<BillingCycleDetailResponse> finalizeCycle(@PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(billingCycleService.finalizeCycle(apartment, id));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<BillingCycleResponse> archive(@PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(billingCycleService.archive(apartment, id));
    }

    @PutMapping("/invoices/{invoiceId}/payment-status")
    public ResponseEntity<InvoiceResponse> setInvoicePaid(@PathVariable Long invoiceId,
            @RequestBody InvoicePaymentStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(billingCycleService.setInvoicePaid(apartment, invoiceId, request.paid()));
    }
}
