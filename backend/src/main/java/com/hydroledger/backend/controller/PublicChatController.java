package com.hydroledger.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.ChatRequest;
import com.hydroledger.backend.dto.ChatResponse;
import com.hydroledger.backend.service.ChatOrchestratorService;
import com.hydroledger.backend.service.ChatPersona;
import com.hydroledger.backend.service.ChatRateLimiterService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicChatController {

    private final ChatOrchestratorService chatOrchestratorService;
    private final ChatRateLimiterService chatRateLimiterService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        chatRateLimiterService.checkAndConsume("ip:" + httpRequest.getRemoteAddr());
        return ResponseEntity.ok(chatOrchestratorService.orchestrate(ChatPersona.LANDING, null, request));
    }
}
