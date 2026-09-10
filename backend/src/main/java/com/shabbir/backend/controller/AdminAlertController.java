package com.shabbir.backend.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shabbir.backend.dto.AlertConfigRequest;
import com.shabbir.backend.dto.AlertConfigResponse;
import com.shabbir.backend.dto.AlertResponse;
import com.shabbir.backend.dto.AlertStatusUpdateRequest;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.AlertStatus;
import com.shabbir.backend.security.UserPrincipal;
import com.shabbir.backend.service.AlertConfigService;
import com.shabbir.backend.service.AlertEngineService;
import com.shabbir.backend.service.ApartmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAlertController {

    private final AlertEngineService alertEngineService;
    private final AlertConfigService alertConfigService;
    private final ApartmentService apartmentService;

    @GetMapping("/alert-config")
    public ResponseEntity<AlertConfigResponse> getConfig(@AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(alertConfigService.getOrDefault(apartment));
    }

    @PutMapping("/alert-config")
    public ResponseEntity<AlertConfigResponse> saveConfig(@Valid @RequestBody AlertConfigRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(alertConfigService.save(apartment, request));
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<AlertResponse>> listAlerts(@RequestParam(required = false) AlertStatus status,
            @AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(alertEngineService.listAlerts(apartment, status));
    }

    @PutMapping("/alerts/{id}/status")
    public ResponseEntity<AlertResponse> updateStatus(@PathVariable Long id,
            @Valid @RequestBody AlertStatusUpdateRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(alertEngineService.updateStatus(apartment, id, request.status()));
    }

    @PostMapping("/alerts/run-now")
    public ResponseEntity<List<AlertResponse>> runNow(@AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        alertEngineService.evaluateApartment(apartment);
        return ResponseEntity.ok(alertEngineService.listAlerts(apartment, null));
    }
}
