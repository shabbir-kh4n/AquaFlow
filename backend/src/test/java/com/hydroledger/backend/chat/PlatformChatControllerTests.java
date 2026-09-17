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

import com.hydroledger.backend.controller.PlatformChatController;
import com.hydroledger.backend.dto.ChatResponse;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.SystemAdmin;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;
import com.hydroledger.backend.security.CustomUserDetailsService;
import com.hydroledger.backend.security.JwtService;
import com.hydroledger.backend.security.SecurityConfig;
import com.hydroledger.backend.security.SystemAdminPrincipal;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.ChatOrchestratorService;
import com.hydroledger.backend.service.ChatPersona;
import com.hydroledger.backend.service.ChatRateLimiterService;

@WebMvcTest(PlatformChatController.class)
@Import(SecurityConfig.class)
class PlatformChatControllerTests {

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

    private SystemAdmin systemAdmin() {
        return SystemAdmin.builder().id(1L).fullName("Sys Admin").email("sysadmin@hydroledger.local").build();
    }

    @Test
    void chatReturnsReplyForAuthenticatedSystemAdmin() throws Exception {
        when(chatOrchestratorService.orchestrate(eq(ChatPersona.PLATFORM), any(), any()))
                .thenReturn(new ChatResponse("The platform has 5 communities."));

        mockMvc.perform(post("/api/platform/chat")
                        .with(user(new SystemAdminPrincipal(systemAdmin())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"How many communities?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("The platform has 5 communities."));
    }

    @Test
    void chatIsForbiddenForAdmin() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(2L).fullName("Admin").email("admin@example.com").build();

        mockMvc.perform(post("/api/platform/chat")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void chatIsForbiddenWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/platform/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isForbidden());
    }
}
