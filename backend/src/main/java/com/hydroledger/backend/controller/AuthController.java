package com.hydroledger.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.AuthResponse;
import com.hydroledger.backend.dto.LoginRequest;
import com.hydroledger.backend.dto.RegisterRequest;
import com.hydroledger.backend.dto.SetPasswordInfoResponse;
import com.hydroledger.backend.dto.SetPasswordRequest;
import com.hydroledger.backend.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/set-password/{token}")
    public ResponseEntity<SetPasswordInfoResponse> getSetPasswordInfo(@PathVariable String token) {
        return ResponseEntity.ok(authService.getSetPasswordInfo(token));
    }

    @PostMapping("/set-password")
    public ResponseEntity<AuthResponse> setPassword(@Valid @RequestBody SetPasswordRequest request) {
        return ResponseEntity.ok(authService.setPassword(request));
    }
}
