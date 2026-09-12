package com.shabbir.backend.chat;

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

import com.shabbir.backend.controller.PlatformChatController;
import com.shabbir.backend.dto.ChatResponse;
import com.shabbir.backend.entity.CommunityAdmin;
import com.shabbir.backend.entity.SystemAdmin;
import com.shabbir.backend.repository.CommunityAdminRepository;
import com.shabbir.backend.repository.ResidentRepository;
import com.shabbir.backend.repository.SystemAdminRepository;
import com.shabbir.backend.security.CustomUserDetailsService;
import com.shabbir.backend.security.JwtService;
import com.shabbir.backend.security.SecurityConfig;
import com.shabbir.backend.security.SystemAdminPrincipal;
import com.shabbir.backend.security.UserPrincipal;
import com.shabbir.backend.service.ChatOrchestratorService;
import com.shabbir.backend.service.ChatPersona;
import com.shabbir.backend.service.ChatRateLimiterService;

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
