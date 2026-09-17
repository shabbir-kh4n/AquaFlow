package com.hydroledger.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.HouseholdResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.ApartmentService;
import com.hydroledger.backend.service.HouseholdService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/households")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class HouseholdController {

    private final HouseholdService householdService;
    private final ApartmentService apartmentService;

    @GetMapping
    public ResponseEntity<List<HouseholdResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(householdService.listForAdmin(apartment));
    }
}
