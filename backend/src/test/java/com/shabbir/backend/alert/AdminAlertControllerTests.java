package com.shabbir.backend.alert;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shabbir.backend.controller.AdminAlertController;
import com.shabbir.backend.dto.AlertConfigResponse;
import com.shabbir.backend.dto.AlertResponse;
import com.shabbir.backend.entity.AlertStatus;
import com.shabbir.backend.entity.AlertType;
import com.shabbir.backend.entity.Apartment;
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
import com.shabbir.backend.service.AlertConfigService;
import com.shabbir.backend.service.AlertEngineService;
import com.shabbir.backend.service.ApartmentService;

@WebMvcTest(AdminAlertController.class)
@Import(SecurityConfig.class)
class AdminAlertControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertEngineService alertEngineService;

    @MockitoBean
    private AlertConfigService alertConfigService;

    @MockitoBean
    private ApartmentService apartmentService;

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
    private final Apartment apartment = Apartment.builder().id(1L).name("Sunrise Towers").admin(admin).build();

    @Test
    void getConfigReturns200() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(alertConfigService.getOrDefault(apartment)).thenReturn(
                new AlertConfigResponse(null, null, new BigDecimal("2.0"), true, true, false, null));

        mockMvc.perform(get("/api/admin/alert-config").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk());
    }

    @Test
    void saveConfigRejectsMissingMultiplier() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);

        mockMvc.perform(put("/api/admin/alert-config")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyThresholdKl\":20,\"thresholdAlertsEnabled\":true,\"outlierAlertsEnabled\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listAlertsReturns200() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(alertEngineService.listAlerts(eq(apartment), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/alerts").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk());
    }

    @Test
    void updateStatusReturns200() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(alertEngineService.updateStatus(eq(apartment), eq(1L), eq(AlertStatus.RESOLVED))).thenReturn(
                new AlertResponse(1L, 1L, "A-101", "Res", "MTR-1", AlertType.THRESHOLD_EXCEEDED, "2026-09",
                        new BigDecimal("15"), new BigDecimal("10"), "msg", AlertStatus.RESOLVED, Instant.now(), Instant.now()));

        mockMvc.perform(put("/api/admin/alerts/1/status")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void runNowTriggersEvaluationAndReturnsList() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(alertEngineService.listAlerts(eq(apartment), isNull())).thenReturn(List.of());

        mockMvc.perform(post("/api/admin/alerts/run-now").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk());
    }

    @Test
    void listAlertsIsForbiddenForResident() throws Exception {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();

        mockMvc.perform(get("/api/admin/alerts").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
    }
}
