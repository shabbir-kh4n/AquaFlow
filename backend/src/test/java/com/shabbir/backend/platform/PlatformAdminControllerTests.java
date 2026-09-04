package com.shabbir.backend.platform;

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

import com.shabbir.backend.controller.PlatformAdminController;
import com.shabbir.backend.dto.CommunitySummaryResponse;
import com.shabbir.backend.dto.SystemMetricsResponse;
import com.shabbir.backend.dto.SystemStatusResponse;
import com.shabbir.backend.entity.CommunityAdmin;
import com.shabbir.backend.entity.CommunityAdminStatus;
import com.shabbir.backend.entity.Resident;
import com.shabbir.backend.entity.SystemAdmin;
import com.shabbir.backend.repository.CommunityAdminRepository;
import com.shabbir.backend.repository.ResidentRepository;
import com.shabbir.backend.repository.SystemAdminRepository;
import com.shabbir.backend.security.CustomUserDetailsService;
import com.shabbir.backend.security.JwtService;
import com.shabbir.backend.security.ResidentPrincipal;
import com.shabbir.backend.security.SecurityConfig;
import com.shabbir.backend.security.SystemAdminPrincipal;
import com.shabbir.backend.security.UserPrincipal;
import com.shabbir.backend.service.SystemAdminService;
import com.shabbir.backend.service.SystemMetricsService;

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
    private JwtService jwtService;

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
                        10L, "Sunrise Towers", 4, 4, Instant.now())
        ));

        mockMvc.perform(get("/api/platform/communities").with(user(new SystemAdminPrincipal(systemAdmin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].apartmentName").value("Sunrise Towers"));
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

    private static SystemMetricsResponse.MetricSummary zeroSummary() {
        return new SystemMetricsResponse.MetricSummary(0, 0, 0, 0);
    }
}
