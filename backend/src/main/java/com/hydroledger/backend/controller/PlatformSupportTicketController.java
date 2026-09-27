package com.hydroledger.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.AddTicketMessageRequest;
import com.hydroledger.backend.dto.SupportTicketDetailResponse;
import com.hydroledger.backend.dto.SupportTicketResponse;
import com.hydroledger.backend.dto.SupportTicketStatusUpdateRequest;
import com.hydroledger.backend.entity.SupportTicketStatus;
import com.hydroledger.backend.service.SupportTicketService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/platform/support-tickets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class PlatformSupportTicketController {

    private final SupportTicketService supportTicketService;

    @GetMapping
    public ResponseEntity<List<SupportTicketResponse>> list(@RequestParam(required = false) SupportTicketStatus status) {
        return ResponseEntity.ok(supportTicketService.listForSystemAdmin(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportTicketDetailResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(supportTicketService.getDetailForSystemAdmin(id));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<SupportTicketResponse> updateStatus(@PathVariable Long id,
            @Valid @RequestBody SupportTicketStatusUpdateRequest request) {
        return ResponseEntity.ok(supportTicketService.updateStatus(id, request.status()));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<SupportTicketDetailResponse> addMessage(@PathVariable Long id,
            @Valid @RequestBody AddTicketMessageRequest request) {
        return ResponseEntity.ok(supportTicketService.addMessageFromSystemAdmin(id, request));
    }
}
