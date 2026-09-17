package com.hydroledger.backend.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hydroledger.backend.controller.AdminChatController;
import com.hydroledger.backend.dto.ChatResponse;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;
import com.hydroledger.backend.security.CustomUserDetailsService;
import com.hydroledger.backend.security.JwtService;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.security.SecurityConfig;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.ChatOrchestratorService;
import com.hydroledger.backend.service.ChatPersona;
import com.hydroledger.backend.service.ChatRateLimiterService;

@WebMvcTest(AdminChatController.class)
@Import(SecurityConfig.class)
class AdminChatControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatOrchestratorService chatOrchestratorService;

    @MockitoBean
    private ChatRateLimiterService chatRateLimiterService;

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

    private CommunityAdmin admin() {
        return CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();
    }

    @Test
    void chatReturnsReplyForAuthenticatedAdmin() throws Exception {
        when(chatOrchestratorService.orchestrate(eq(ChatPersona.ADMIN), any(), any()))
                .thenReturn(new ChatResponse("You have 12 households."));

        mockMvc.perform(post("/api/admin/chat")
                        .with(user(new UserPrincipal(admin())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"How many households?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("You have 12 households."));
    }

    @Test
    void chatIsForbiddenForResident() throws Exception {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();

        mockMvc.perform(post("/api/admin/chat")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void chatIsForbiddenWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isForbidden());
    }
}
