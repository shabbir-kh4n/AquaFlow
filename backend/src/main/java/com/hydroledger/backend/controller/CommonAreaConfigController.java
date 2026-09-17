package com.hydroledger.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.CommonAreaConfigRequest;
import com.hydroledger.backend.dto.CommonAreaConfigResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.ApartmentService;
import com.hydroledger.backend.service.CommonAreaConfigService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/common-area-config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CommonAreaConfigController {

    private final CommonAreaConfigService commonAreaConfigService;
    private final ApartmentService apartmentService;

    @GetMapping
    public ResponseEntity<CommonAreaConfigResponse> get(@AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(commonAreaConfigService.getOrDefault(apartment));
    }

    @PutMapping
    public ResponseEntity<CommonAreaConfigResponse> save(@Valid @RequestBody CommonAreaConfigRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(commonAreaConfigService.save(apartment, request));
    }
}
