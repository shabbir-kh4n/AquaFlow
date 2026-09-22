package com.hydroledger.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.PushSubscribeRequest;
import com.hydroledger.backend.dto.PushUnsubscribeRequest;
import com.hydroledger.backend.dto.VapidPublicKeyResponse;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.service.PushSubscriptionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/resident/push")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
public class ResidentPushController {

    private final PushSubscriptionService pushSubscriptionService;

    @Value("${app.push.vapid-public-key}")
    private String vapidPublicKey;

    @GetMapping("/vapid-public-key")
    public ResponseEntity<VapidPublicKeyResponse> getVapidPublicKey() {
        return ResponseEntity.ok(new VapidPublicKeyResponse(vapidPublicKey));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(@AuthenticationPrincipal ResidentPrincipal principal,
            @Valid @RequestBody PushSubscribeRequest request) {
        pushSubscriptionService.subscribe(principal.getUser(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal ResidentPrincipal principal,
            @Valid @RequestBody PushUnsubscribeRequest request) {
        pushSubscriptionService.unsubscribe(principal.getUser(), request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
