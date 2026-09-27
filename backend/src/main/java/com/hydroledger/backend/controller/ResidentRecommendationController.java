package com.hydroledger.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hydroledger.backend.dto.ResidentRecommendationResponse;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.service.ResidentRecommendationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/resident")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
public class ResidentRecommendationController {

    private final ResidentRecommendationService residentRecommendationService;

    @GetMapping("/recommendations")
    public ResponseEntity<ResidentRecommendationResponse> getRecommendations(
            @AuthenticationPrincipal ResidentPrincipal principal,
            @RequestParam(required = false) String lang) {
        return ResponseEntity.ok(residentRecommendationService.getForResident(principal.getUser(), lang));
    }
}
