package com.hydroledger.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.ChatRequest;
import com.hydroledger.backend.dto.ChatResponse;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.ChatOrchestratorService;
import com.hydroledger.backend.service.ChatPersona;
import com.hydroledger.backend.service.ChatRateLimiterService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminChatController {

    private final ChatOrchestratorService chatOrchestratorService;
    private final ChatRateLimiterService chatRateLimiterService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        chatRateLimiterService.checkAndConsume("admin:" + principal.getUser().getId());
        return ResponseEntity.ok(chatOrchestratorService.orchestrate(ChatPersona.ADMIN, principal.getUser(), request));
    }
}
