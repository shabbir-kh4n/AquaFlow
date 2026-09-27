package com.hydroledger.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.AddTicketMessageRequest;
import com.hydroledger.backend.dto.CreateSupportTicketRequest;
import com.hydroledger.backend.dto.SupportTicketDetailResponse;
import com.hydroledger.backend.dto.SupportTicketResponse;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.service.SupportTicketService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/resident/support-tickets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
public class ResidentSupportTicketController {

    private final SupportTicketService supportTicketService;

    @GetMapping
    public ResponseEntity<List<SupportTicketResponse>> list(@AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(supportTicketService.listForResident(principal.getUser()));
    }

    @PostMapping
    public ResponseEntity<SupportTicketResponse> create(@Valid @RequestBody CreateSupportTicketRequest request,
            @AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(supportTicketService.createFromResident(principal.getUser(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportTicketDetailResponse> getDetail(@PathVariable Long id,
            @AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(supportTicketService.getDetailForResident(id, principal.getUser()));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<SupportTicketDetailResponse> addMessage(@PathVariable Long id,
            @Valid @RequestBody AddTicketMessageRequest request, @AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(supportTicketService.addMessageFromResident(id, principal.getUser(), request));
    }
}
