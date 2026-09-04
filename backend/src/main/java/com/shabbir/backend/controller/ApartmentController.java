package com.shabbir.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shabbir.backend.dto.ApartmentRequest;
import com.shabbir.backend.dto.ApartmentResponse;
import com.shabbir.backend.security.UserPrincipal;
import com.shabbir.backend.service.ApartmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/apartment")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ApartmentController {

    private final ApartmentService apartmentService;

    @GetMapping
    public ResponseEntity<ApartmentResponse> get(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(apartmentService.getForAdmin(principal.getUser()));
    }

    @PutMapping
    public ResponseEntity<ApartmentResponse> update(@Valid @RequestBody ApartmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(apartmentService.update(request, principal.getUser()));
    }
}
