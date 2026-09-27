package com.hydroledger.backend.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.hydroledger.backend.controller.PlatformSupportTicketController;
import com.hydroledger.backend.dto.SupportTicketDetailResponse;
import com.hydroledger.backend.dto.SupportTicketResponse;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.SupportTicketRole;
import com.hydroledger.backend.entity.SupportTicketStatus;
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
import com.hydroledger.backend.service.SupportTicketService;

@WebMvcTest(PlatformSupportTicketController.class)
@Import(SecurityConfig.class)
class PlatformSupportTicketControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupportTicketService supportTicketService;

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
    void listReturnsOkForSystemAdmin() throws Exception {
        when(supportTicketService.listForSystemAdmin(isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/platform/support-tickets").with(user(new SystemAdminPrincipal(systemAdmin))))
                .andExpect(status().isOk());
    }

    @Test
    void listIsForbiddenForCommunityAdmin() throws Exception {
        mockMvc.perform(get("/api/platform/support-tickets").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listIsForbiddenForResident() throws Exception {
        mockMvc.perform(get("/api/platform/support-tickets").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatusReturnsOkForSystemAdmin() throws Exception {
        when(supportTicketService.updateStatus(eq(1L), eq(SupportTicketStatus.RESOLVED))).thenReturn(
                new SupportTicketResponse(1L, 1L, "Sunrise Towers", SupportTicketRole.RESIDENT, "Res",
                        "Leaking tap", false, true, SupportTicketStatus.RESOLVED, Instant.now(), Instant.now(), Instant.now(),
                        "Leaking tap"));

        mockMvc.perform(put("/api/platform/support-tickets/1/status")
                        .with(user(new SystemAdminPrincipal(systemAdmin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateStatusIsForbiddenForCommunityAdmin() throws Exception {
        mockMvc.perform(put("/api/platform/support-tickets/1/status")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addMessageReturnsOkForSystemAdmin() throws Exception {
        when(supportTicketService.addMessageFromSystemAdmin(eq(1L), any())).thenReturn(
                new SupportTicketDetailResponse(1L, 1L, "Sunrise Towers", SupportTicketRole.RESIDENT, "Res",
                        "Leaking tap", false, true, SupportTicketStatus.IN_PROGRESS, Instant.now(), Instant.now(), null, List.of()));

        mockMvc.perform(post("/api/platform/support-tickets/1/messages")
                        .with(user(new SystemAdminPrincipal(systemAdmin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Looking into this.\"}"))
                .andExpect(status().isOk());
    }
}
