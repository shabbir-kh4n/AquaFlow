package com.hydroledger.backend.platform;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hydroledger.backend.controller.PlatformAdminController;
import com.hydroledger.backend.dto.CommunitySummaryResponse;
import com.hydroledger.backend.dto.PlatformReportResponse;
import com.hydroledger.backend.dto.SystemMetricsResponse;
import com.hydroledger.backend.dto.SystemStatusResponse;
import com.hydroledger.backend.dto.VerificationResponse;
import com.hydroledger.backend.entity.VerificationStatus;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.CommunityAdminStatus;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.SystemAdmin;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;
import com.hydroledger.backend.security.CustomUserDetailsService;
import com.hydroledger.backend.security.JwtService;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.security.SecurityConfig;
import com.hydroledger.backend.security.SystemAdminPrincipal;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.AdminVerificationService;
import com.hydroledger.backend.service.PlatformReportService;
import com.hydroledger.backend.service.SystemAdminService;
import com.hydroledger.backend.service.SystemMetricsService;

@WebMvcTest(PlatformAdminController.class)
@Import(SecurityConfig.class)
class PlatformAdminControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemAdminService systemAdminService;

    @MockitoBean
    private SystemMetricsService systemMetricsService;

    @MockitoBean
    private PlatformReportService platformReportService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AdminVerificationService adminVerificationService;

    @MockitoBean
    private CommunityAdminRepository communityAdminRepository;

    @MockitoBean
    private ResidentRepository residentRepository;

    @MockitoBean
    private SystemAdminRepository systemAdminRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private final SystemAdmin systemAdmin = SystemAdmin.builder().id(1L).fullName("Root").email("root@hydroledger.local").build();
    private final CommunityAdmin admin = CommunityAdmin.builder().id(2L).fullName("Jane Admin").email("jane@example.com").build();
    private final Resident resident = Resident.builder().id(3L).fullName("Res").email("res@example.com").build();

    @Test
    void listCommunitiesReturnsOkForSystemAdmin() throws Exception {
        when(systemAdminService.listCommunities()).thenReturn(List.of(
                new CommunitySummaryResponse(2L, "Jane Admin", "jane@example.com", CommunityAdminStatus.ACTIVE,
                        10L, "Sunrise Towers", 4, 4, Instant.now(), null, com.hydroledger.backend.entity.VerificationStatus.NOT_SUBMITTED)
        ));

        mockMvc.perform(get("/api/platform/communities").with(user(new SystemAdminPrincipal(systemAdmin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].apartmentName").value("Sunrise Towers"));
    }

    @Test
    void rejectVerificationRequiresAReason() throws Exception {
        mockMvc.perform(put("/api/platform/communities/2/verification/reject")
                        .with(user(new SystemAdminPrincipal(systemAdmin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectVerificationPassesReasonToService() throws Exception {
        when(adminVerificationService.reject(2L, "Blurry scan")).thenReturn(new VerificationResponse(
                VerificationStatus.REJECTED, "GST", "X1", null, "S", "A", "C", "St", "1", 10, null, "a.pdf", 10L, "Blurry scan", Instant.now(), Instant.now()));

        mockMvc.perform(put("/api/platform/communities/2/verification/reject")
                        .with(user(new SystemAdminPrincipal(systemAdmin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Blurry scan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Blurry scan"));
    }

    @Test
    void approveVerificationIsForbiddenForCommunityAdmin() throws Exception {
        mockMvc.perform(put("/api/platform/communities/2/verification/approve").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listCommunitiesIsForbiddenForCommunityAdmin() throws Exception {
        mockMvc.perform(get("/api/platform/communities").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listCommunitiesIsForbiddenForResident() throws Exception {
        mockMvc.perform(get("/api/platform/communities").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatusIsForbiddenForCommunityAdmin() throws Exception {
        mockMvc.perform(put("/api/platform/communities/2/status")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteCommunityIsForbiddenForResident() throws Exception {
        mockMvc.perform(delete("/api/platform/communities/2").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemStatusReturnsOkForSystemAdmin() throws Exception {
        when(systemAdminService.getSystemStatus()).thenReturn(new SystemStatusResponse(true, 1000L, "1.0.0"));

        mockMvc.perform(get("/api/platform/system-status").with(user(new SystemAdminPrincipal(systemAdmin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseUp").value(true));
    }

    @Test
    void systemStatusIsForbiddenForCommunityAdmin() throws Exception {
        mockMvc.perform(get("/api/platform/system-status").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemMetricsReturnsOkForSystemAdmin() throws Exception {
        when(systemMetricsService.getMetrics("1h")).thenReturn(new SystemMetricsResponse(
                "1h", List.of(),
                zeroSummary(), zeroSummary(), zeroSummary(), zeroSummary(), zeroSummary(), zeroSummary(), zeroSummary()));

        mockMvc.perform(get("/api/platform/system-metrics").param("range", "1h")
                        .with(user(new SystemAdminPrincipal(systemAdmin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("1h"));
    }

    @Test
    void systemMetricsIsForbiddenForCommunityAdmin() throws Exception {
        mockMvc.perform(get("/api/platform/system-metrics").param("range", "1h")
                        .with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportReturnsOkForSystemAdmin() throws Exception {
        when(platformReportService.getReport(null, null, null)).thenReturn(new PlatformReportResponse(
                new PlatformReportResponse.ReportRangeInfo("3mo", "2026-07", "2026-09", List.of("2026-07", "2026-08", "2026-09")),
                new PlatformReportResponse.PlatformReportSummary(1, 4, java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(500),
                        java.math.BigDecimal.ONE, java.math.BigDecimal.valueOf(125)),
                List.of(),
                List.of()));

        mockMvc.perform(get("/api/platform/reports").with(user(new SystemAdminPrincipal(systemAdmin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range.key").value("3mo"));
    }

    @Test
    void reportIsForbiddenForCommunityAdmin() throws Exception {
        mockMvc.perform(get("/api/platform/reports").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportIsForbiddenForResident() throws Exception {
        mockMvc.perform(get("/api/platform/reports").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
    }

    private static SystemMetricsResponse.MetricSummary zeroSummary() {
        return new SystemMetricsResponse.MetricSummary(0, 0, 0, 0);
    }
}
