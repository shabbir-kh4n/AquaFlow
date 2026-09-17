package com.hydroledger.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.ConsumptionComparisonPoint;
import com.hydroledger.backend.dto.WaterUsageReadingResponse;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.service.WaterUsageReadingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/resident")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
public class ResidentUsageHistoryController {

    private final WaterUsageReadingService waterUsageReadingService;

    @GetMapping("/usage-history")
    public ResponseEntity<List<WaterUsageReadingResponse>> getUsageHistory(@AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(waterUsageReadingService.listForResident(principal.getUser()));
    }

    @GetMapping("/usage-comparison")
    public ResponseEntity<List<ConsumptionComparisonPoint>> getUsageComparison(@AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(waterUsageReadingService.getComparisonForResident(principal.getUser()));
    }
}
