package com.hydroledger.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.TariffPlanRequest;
import com.hydroledger.backend.dto.TariffPlanResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.ApartmentService;
import com.hydroledger.backend.service.TariffPlanService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/tariff-plan")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TariffPlanController {

    private final TariffPlanService tariffPlanService;
    private final ApartmentService apartmentService;

    @GetMapping
    public ResponseEntity<TariffPlanResponse> get(@AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(tariffPlanService.getOrDefault(apartment));
    }

    @PutMapping
    public ResponseEntity<TariffPlanResponse> save(@Valid @RequestBody TariffPlanRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(tariffPlanService.save(apartment, request));
    }
}
