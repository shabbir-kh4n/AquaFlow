package com.hydroledger.backend.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.ResidentReportResponse;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.service.ResidentReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/resident")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
public class ResidentReportController {

    private final ResidentReportService residentReportService;

    /** Personal usage & spending report. Either a preset {@code range} (3mo/6mo/12mo) or both {@code from}/{@code to} (YYYY-MM). */
    @GetMapping("/reports")
    public ResponseEntity<ResidentReportResponse> getReport(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(residentReportService.getReport(principal.getUser(), range, from, to));
    }

    // A literal path segment, so Spring prefers it over ResidentBillingController's /invoices/{id}/pdf.
    @GetMapping("/invoices/statement/pdf")
    public ResponseEntity<byte[]> downloadStatementPdf(@AuthenticationPrincipal ResidentPrincipal principal) {
        byte[] pdf = residentReportService.generateStatementPdf(principal.getUser());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"account-statement.pdf\"")
                .body(pdf);
    }
}
