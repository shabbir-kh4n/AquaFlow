package com.shabbir.backend.resident;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shabbir.backend.controller.ResidentDashboardController;
import com.shabbir.backend.dto.ResidentDashboardResponse;
import com.shabbir.backend.entity.CommunityAdmin;
import com.shabbir.backend.entity.Resident;
import com.shabbir.backend.repository.CommunityAdminRepository;
import com.shabbir.backend.repository.ResidentRepository;
import com.shabbir.backend.repository.SystemAdminRepository;
import com.shabbir.backend.security.CustomUserDetailsService;
import com.shabbir.backend.security.JwtService;
import com.shabbir.backend.security.ResidentPrincipal;
import com.shabbir.backend.security.SecurityConfig;
import com.shabbir.backend.security.UserPrincipal;
import com.shabbir.backend.service.ResidentDashboardService;

@WebMvcTest(ResidentDashboardController.class)
@Import(SecurityConfig.class)
class ResidentDashboardControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResidentDashboardService residentDashboardService;

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

    private final Resident resident = Resident.builder().id(1L).fullName("Res Ident").email("res@example.com").build();

    @Test
    void dashboardReturns200WithReading() throws Exception {
        when(residentDashboardService.getDashboard(any(Resident.class)))
                .thenReturn(new ResidentDashboardResponse("Sunrise Towers", "A-101", "MTR-0001",
                        new BigDecimal("120.000"), LocalDate.of(2026, 2, 1)));

        mockMvc.perform(get("/api/resident/dashboard").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.communityName").value("Sunrise Towers"))
                .andExpect(jsonPath("$.latestReadingValue").value(120.0));
    }

    @Test
    void dashboardReturns200WithoutReading() throws Exception {
        when(residentDashboardService.getDashboard(any(Resident.class)))
                .thenReturn(new ResidentDashboardResponse("Sunrise Towers", "A-101", "MTR-0001", null, null));

        mockMvc.perform(get("/api/resident/dashboard").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestReadingValue").doesNotExist());
    }

    @Test
    void dashboardIsForbiddenForAdmin() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Admin").email("admin@example.com").build();

        mockMvc.perform(get("/api/resident/dashboard").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }
}
