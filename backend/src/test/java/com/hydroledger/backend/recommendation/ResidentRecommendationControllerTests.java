package com.hydroledger.backend.recommendation;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hydroledger.backend.controller.ResidentRecommendationController;
import com.hydroledger.backend.dto.ResidentRecommendationResponse;
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
import com.hydroledger.backend.service.ResidentRecommendationService;

@WebMvcTest(ResidentRecommendationController.class)
@Import(SecurityConfig.class)
class ResidentRecommendationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResidentRecommendationService residentRecommendationService;

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
    void getRecommendationsReturns200ForResident() throws Exception {
        when(residentRecommendationService.getForResident(resident, null)).thenReturn(
                new ResidentRecommendationResponse(List.of("Fix leaking taps"), Instant.now(), "2026-09"));

        mockMvc.perform(get("/api/resident/recommendations").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tips[0]").value("Fix leaking taps"))
                .andExpect(jsonPath("$.sourcePeriodMonth").value("2026-09"));
    }

    @Test
    void getRecommendationsFallsBackToEmptyTipsWhenNoneGeneratedYet() throws Exception {
        when(residentRecommendationService.getForResident(resident, null)).thenReturn(
                new ResidentRecommendationResponse(List.of(), null, null));

        mockMvc.perform(get("/api/resident/recommendations").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tips").isEmpty());
    }

    @Test
    void getRecommendationsIsForbiddenForAdmin() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Admin").email("admin@example.com").build();

        mockMvc.perform(get("/api/resident/recommendations").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }
}
