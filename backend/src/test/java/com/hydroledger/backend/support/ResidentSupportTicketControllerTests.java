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

import com.hydroledger.backend.controller.ResidentSupportTicketController;
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

@WebMvcTest(ResidentSupportTicketController.class)
@Import(SecurityConfig.class)
class ResidentSupportTicketControllerTests {

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

    private final Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").build();

    @Test
    void listReturns200ForResident() throws Exception {
        when(supportTicketService.listForResident(resident)).thenReturn(List.of());

        mockMvc.perform(get("/api/resident/support-tickets").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk());
    }

    @Test
    void listIsForbiddenForAdmin() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Admin").email("admin@example.com").build();

        mockMvc.perform(get("/api/resident/support-tickets").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReturns200() throws Exception {
        when(supportTicketService.createFromResident(eq(resident), any())).thenReturn(
                new SupportTicketResponse(1L, 1L, "Sunrise Towers", SupportTicketRole.RESIDENT, "Res",
                        "Leaking tap", false, true, SupportTicketStatus.OPEN, Instant.now(), Instant.now(), null, "Leaking tap"));

        mockMvc.perform(post("/api/resident/support-tickets")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Leaking tap\",\"message\":\"Leaking tap\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void createRejectsBlankSubject() throws Exception {
        mockMvc.perform(post("/api/resident/support-tickets")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"\",\"message\":\"Leaking tap\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addMessageReturns200() throws Exception {
        when(supportTicketService.addMessageFromResident(eq(1L), eq(resident), any())).thenReturn(
                new SupportTicketDetailResponse(1L, 1L, "Sunrise Towers", SupportTicketRole.RESIDENT, "Res",
                        "Leaking tap", false, true, SupportTicketStatus.IN_PROGRESS, Instant.now(), Instant.now(), null, List.of()));

        mockMvc.perform(post("/api/resident/support-tickets/1/messages")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Any update?\"}"))
                .andExpect(status().isOk());
    }
}
