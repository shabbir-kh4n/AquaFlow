package com.shabbir.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shabbir.backend.dto.TariffPlanRequest;
import com.shabbir.backend.dto.TariffPlanResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.security.UserPrincipal;
import com.shabbir.backend.service.ApartmentService;
import com.shabbir.backend.service.TariffPlanService;

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
