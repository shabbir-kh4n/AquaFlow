package com.hydroledger.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.AnnouncementResponse;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.service.AnnouncementService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/resident")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
public class ResidentAnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping("/announcements")
    public ResponseEntity<List<AnnouncementResponse>> list(@AuthenticationPrincipal ResidentPrincipal principal) {
        return ResponseEntity.ok(announcementService.listForResident(principal.getUser()));
    }
}
