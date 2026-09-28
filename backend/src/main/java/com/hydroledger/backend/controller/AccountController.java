package com.hydroledger.backend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.ChangePasswordRequest;
import com.hydroledger.backend.service.AccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Logged-in account operations for every role. /api/auth/** is permitAll at the filter level,
 * so authentication is enforced here with @PreAuthorize.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        accountService.changePassword(principal, request);
        return ResponseEntity.ok(Map.of("message", "Password updated"));
    }
}
