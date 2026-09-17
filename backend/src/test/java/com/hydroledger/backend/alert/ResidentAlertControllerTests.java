package com.hydroledger.backend.alert;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hydroledger.backend.controller.ResidentAlertController;
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
import com.hydroledger.backend.service.AlertEngineService;

@WebMvcTest(ResidentAlertController.class)
@Import(SecurityConfig.class)
class ResidentAlertControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertEngineService alertEngineService;

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
    void getAlertsReturns200ForResident() throws Exception {
        when(alertEngineService.listForResident(resident)).thenReturn(List.of());

        mockMvc.perform(get("/api/resident/alerts").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk());
    }

    @Test
    void getAlertsIsForbiddenForAdmin() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Admin").email("admin@example.com").build();

        mockMvc.perform(get("/api/resident/alerts").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }
}
