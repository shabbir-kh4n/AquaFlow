package com.shabbir.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shabbir.backend.dto.AlertResponse;
import com.shabbir.backend.security.ResidentPrincipal;
import com.shabbir.backend.service.AlertEngineService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/resident")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
public class ResidentAlertController {

    private final AlertEngineService alertEngineService;

    @GetMapping("/alerts")
    public ResponseEntity<List<AlertResponse>> getAlerts(@AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(alertEngineService.listForResident(principal.getUser()));
    }
}
