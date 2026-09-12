package com.shabbir.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shabbir.backend.dto.ChatRequest;
import com.shabbir.backend.dto.ChatResponse;
import com.shabbir.backend.security.SystemAdminPrincipal;
import com.shabbir.backend.service.ChatOrchestratorService;
import com.shabbir.backend.service.ChatPersona;
import com.shabbir.backend.service.ChatRateLimiterService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class PlatformChatController {

    private final ChatOrchestratorService chatOrchestratorService;
    private final ChatRateLimiterService chatRateLimiterService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal SystemAdminPrincipal principal) {
        chatRateLimiterService.checkAndConsume("sysadmin:" + principal.getUser().getId());
        return ResponseEntity.ok(chatOrchestratorService.orchestrate(ChatPersona.PLATFORM, principal.getUser(), request));
    }
}
