package com.hydroledger.backend.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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

import com.hydroledger.backend.controller.PublicChatController;
import com.hydroledger.backend.dto.ChatResponse;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;
import com.hydroledger.backend.security.CustomUserDetailsService;
import com.hydroledger.backend.security.JwtService;
import com.hydroledger.backend.security.SecurityConfig;
import com.hydroledger.backend.service.ChatOrchestratorService;
import com.hydroledger.backend.service.ChatPersona;
import com.hydroledger.backend.service.ChatRateLimiterService;

@WebMvcTest(PublicChatController.class)
@Import(SecurityConfig.class)
class PublicChatControllerTests {

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

    @Test
    void chatWorksWithNoAuthenticationHeader() throws Exception {
        when(chatOrchestratorService.orchestrate(eq(ChatPersona.LANDING), any(), any()))
                .thenReturn(new ChatResponse("Welcome to HydroLedger!"));

        mockMvc.perform(post("/api/public/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What is HydroLedger?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Welcome to HydroLedger!"));
    }

    @Test
    void chatRejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/public/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
