package com.shabbir.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shabbir.backend.dto.ResidentDashboardResponse;
import com.shabbir.backend.security.ResidentPrincipal;
import com.shabbir.backend.service.ResidentDashboardService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/resident")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
public class ResidentDashboardController {

    private final ResidentDashboardService residentDashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<ResidentDashboardResponse> getDashboard(@AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(residentDashboardService.getDashboard(principal.getUser()));
    }
}
