package com.hydroledger.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.AvatarUpdateRequest;
import com.hydroledger.backend.dto.ResidentProfileResponse;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.service.ResidentProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/resident/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
public class ResidentProfileController {

    private final ResidentProfileService residentProfileService;

    @GetMapping
    public ResponseEntity<ResidentProfileResponse> get(@AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(residentProfileService.get(principal.getUser()));
    }

    @PutMapping("/avatar")
    public ResponseEntity<ResidentProfileResponse> updateAvatar(@Valid @RequestBody AvatarUpdateRequest request,
            @AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(residentProfileService.updateAvatar(principal.getUser(), request.avatarKey()));
    }
}
