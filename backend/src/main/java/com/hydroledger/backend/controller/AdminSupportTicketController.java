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
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.SupportTicketService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/support-tickets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSupportTicketController {

    private final SupportTicketService supportTicketService;

    @GetMapping
    public ResponseEntity<List<SupportTicketResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(supportTicketService.listForAdmin(principal.getUser()));
    }

    @PostMapping
    public ResponseEntity<SupportTicketResponse> create(@Valid @RequestBody CreateSupportTicketRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(supportTicketService.createFromAdmin(principal.getUser(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportTicketDetailResponse> getDetail(@PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(supportTicketService.getDetailForAdmin(id, principal.getUser()));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<SupportTicketDetailResponse> addMessage(@PathVariable Long id,
            @Valid @RequestBody AddTicketMessageRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(supportTicketService.addMessageFromAdmin(id, principal.getUser(), request));
    }

    // "Resident Tickets" inbox: tickets residents in this admin's own community addressed to
    // Community Admin - separate from the admin's own tickets to System Admin above.

    @GetMapping("/inbox")
    public ResponseEntity<List<SupportTicketResponse>> listInbox(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(supportTicketService.listInboxForAdmin(principal.getUser()));
    }

    @GetMapping("/inbox/{id}")
    public ResponseEntity<SupportTicketDetailResponse> getInboxDetail(@PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(supportTicketService.getInboxDetailForAdmin(id, principal.getUser()));
    }

    @PostMapping("/inbox/{id}/messages")
    public ResponseEntity<SupportTicketDetailResponse> addInboxMessage(@PathVariable Long id,
            @Valid @RequestBody AddTicketMessageRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(supportTicketService.addInboxMessageFromAdmin(id, principal.getUser(), request));
    }
}
