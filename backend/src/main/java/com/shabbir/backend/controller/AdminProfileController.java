package com.shabbir.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shabbir.backend.dto.AdminProfileRequest;
import com.shabbir.backend.dto.AdminProfileResponse;
import com.shabbir.backend.security.UserPrincipal;
import com.shabbir.backend.service.AdminProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProfileController {

    private final AdminProfileService adminProfileService;

    @GetMapping
    public ResponseEntity<AdminProfileResponse> get(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(adminProfileService.get(principal.getUser()));
    }

    @PutMapping
    public ResponseEntity<AdminProfileResponse> update(@Valid @RequestBody AdminProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(adminProfileService.update(request, principal.getUser()));
    }
}
