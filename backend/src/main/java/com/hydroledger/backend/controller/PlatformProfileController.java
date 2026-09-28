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
import com.hydroledger.backend.dto.PlatformProfileResponse;
import com.hydroledger.backend.security.SystemAdminPrincipal;
import com.hydroledger.backend.service.PlatformProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/platform/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class PlatformProfileController {

    private final PlatformProfileService platformProfileService;

    @GetMapping
    public ResponseEntity<PlatformProfileResponse> get(@AuthenticationPrincipal SystemAdminPrincipal principal) {
        return ResponseEntity.ok(platformProfileService.get(principal.getUser()));
    }

    // Only the avatar is editable; name/email of the seeded system admin are display-only.
    @PutMapping
    public ResponseEntity<PlatformProfileResponse> updateAvatar(@Valid @RequestBody AvatarUpdateRequest request,
            @AuthenticationPrincipal SystemAdminPrincipal principal) {
        return ResponseEntity.ok(platformProfileService.updateAvatar(principal.getUser(), request.avatarKey()));
    }
}
