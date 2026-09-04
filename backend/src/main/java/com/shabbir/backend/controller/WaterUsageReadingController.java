package com.shabbir.backend.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.shabbir.backend.dto.CsvUploadResultResponse;
import com.shabbir.backend.dto.WaterUsageReadingRequest;
import com.shabbir.backend.dto.WaterUsageReadingResponse;
import com.shabbir.backend.security.UserPrincipal;
import com.shabbir.backend.service.WaterUsageReadingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/usage-readings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class WaterUsageReadingController {

    private final WaterUsageReadingService waterUsageReadingService;

    @GetMapping
    public ResponseEntity<List<WaterUsageReadingResponse>> search(
            @RequestParam(required = false) String meterNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(waterUsageReadingService.search(meterNumber, from, to, principal.getUser()));
    }

    @PostMapping
    public ResponseEntity<WaterUsageReadingResponse> record(@Valid @RequestBody WaterUsageReadingRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(waterUsageReadingService.recordManual(request, principal.getUser()));
    }

    @PostMapping("/bulk-csv")
    public ResponseEntity<CsvUploadResultResponse> bulkUploadCsv(@RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(waterUsageReadingService.bulkUploadCsv(file, principal.getUser()));
    }
}
