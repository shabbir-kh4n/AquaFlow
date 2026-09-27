package com.hydroledger.backend.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.hydroledger.backend.controller.AdminSupportTicketController;
import com.hydroledger.backend.dto.SupportTicketDetailResponse;
import com.hydroledger.backend.dto.SupportTicketResponse;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.SupportTicketRole;
import com.hydroledger.backend.entity.SupportTicketStatus;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;
import com.hydroledger.backend.security.CustomUserDetailsService;
import com.hydroledger.backend.security.JwtService;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.security.SecurityConfig;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.SupportTicketService;

@WebMvcTest(AdminSupportTicketController.class)
@Import(SecurityConfig.class)
class AdminSupportTicketControllerTests {

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

    private final CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();

    @Test
    void listReturns200ForAdmin() throws Exception {
        when(supportTicketService.listForAdmin(admin)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/support-tickets").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk());
    }

    @Test
    void listIsForbiddenForResident() throws Exception {
        Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").build();

        mockMvc.perform(get("/api/admin/support-tickets").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReturns200() throws Exception {
        when(supportTicketService.createFromAdmin(eq(admin), any())).thenReturn(
                new SupportTicketResponse(1L, 1L, "Sunrise Towers", SupportTicketRole.ADMIN, "Jane Admin",
                        "Billing question", false, true, SupportTicketStatus.OPEN, Instant.now(), Instant.now(), null,
                        "Billing question"));

        mockMvc.perform(post("/api/admin/support-tickets")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Billing question\",\"message\":\"Billing question\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void createRejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/admin/support-tickets")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Billing question\",\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listInboxReturns200ForAdmin() throws Exception {
        when(supportTicketService.listInboxForAdmin(admin)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/support-tickets/inbox").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk());
    }

    @Test
    void addInboxMessageReturns200ForAdmin() throws Exception {
        when(supportTicketService.addInboxMessageFromAdmin(eq(1L), eq(admin), any())).thenReturn(
                new SupportTicketDetailResponse(1L, 1L, "Sunrise Towers", SupportTicketRole.RESIDENT, "Res",
                        "Leaking tap", true, false, SupportTicketStatus.IN_PROGRESS, Instant.now(), Instant.now(),
                        null, List.of()));

        mockMvc.perform(post("/api/admin/support-tickets/inbox/1/messages")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Looking into this.\"}"))
                .andExpect(status().isOk());
    }
}
