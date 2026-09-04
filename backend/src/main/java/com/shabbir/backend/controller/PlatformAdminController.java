package com.shabbir.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shabbir.backend.dto.CommunityDetailResponse;
import com.shabbir.backend.dto.CommunityStatusUpdateRequest;
import com.shabbir.backend.dto.CommunitySummaryResponse;
import com.shabbir.backend.dto.SystemMetricsResponse;
import com.shabbir.backend.dto.SystemStatusResponse;
import com.shabbir.backend.service.SystemAdminService;
import com.shabbir.backend.service.SystemMetricsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class PlatformAdminController {

    private final SystemAdminService systemAdminService;
    private final SystemMetricsService systemMetricsService;

    @GetMapping("/communities")
    public ResponseEntity<List<CommunitySummaryResponse>> listCommunities() {
        return ResponseEntity.ok(systemAdminService.listCommunities());
    }

    @GetMapping("/communities/{adminId}")
    public ResponseEntity<CommunityDetailResponse> getCommunity(@PathVariable Long adminId) {
        return ResponseEntity.ok(systemAdminService.getCommunityDetail(adminId));
    }

    @PutMapping("/communities/{adminId}/status")
    public ResponseEntity<CommunitySummaryResponse> updateStatus(
            @PathVariable Long adminId,
            @Valid @RequestBody CommunityStatusUpdateRequest request) {
        return ResponseEntity.ok(systemAdminService.updateStatus(adminId, request.status()));
    }

    @DeleteMapping("/communities/{adminId}")
    public ResponseEntity<Void> deleteCommunity(@PathVariable Long adminId) {
        systemAdminService.deleteCommunityAdmin(adminId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/system-status")
    public ResponseEntity<SystemStatusResponse> systemStatus() {
        return ResponseEntity.ok(systemAdminService.getSystemStatus());
    }

    @GetMapping("/system-metrics")
    public ResponseEntity<SystemMetricsResponse> systemMetrics(@RequestParam String range) {
        return ResponseEntity.ok(systemMetricsService.getMetrics(range));
    }
}
