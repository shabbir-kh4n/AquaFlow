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
import com.shabbir.backend.security.ResidentPrincipal;
import com.shabbir.backend.service.ChatOrchestratorService;
import com.shabbir.backend.service.ChatPersona;
import com.shabbir.backend.service.ChatRateLimiterService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/resident")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
public class ResidentChatController {

    private final ChatOrchestratorService chatOrchestratorService;
    private final ChatRateLimiterService chatRateLimiterService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal ResidentPrincipal principal) {
        chatRateLimiterService.checkAndConsume("resident:" + principal.getUser().getId());
        return ResponseEntity.ok(chatOrchestratorService.orchestrate(ChatPersona.RESIDENT, principal.getUser(), request));
    }
}
