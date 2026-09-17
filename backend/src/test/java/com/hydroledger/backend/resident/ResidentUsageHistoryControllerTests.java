package com.hydroledger.backend.resident;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hydroledger.backend.controller.ResidentUsageHistoryController;
import com.hydroledger.backend.dto.ConsumptionComparisonPoint;
import com.hydroledger.backend.dto.WaterUsageReadingResponse;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.ReadingSource;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;
import com.hydroledger.backend.security.CustomUserDetailsService;
import com.hydroledger.backend.security.JwtService;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.security.SecurityConfig;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.WaterUsageReadingService;

@WebMvcTest(ResidentUsageHistoryController.class)
@Import(SecurityConfig.class)
class ResidentUsageHistoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WaterUsageReadingService waterUsageReadingService;

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
    void usageHistoryReturns200WithData() throws Exception {
        when(waterUsageReadingService.listForResident(any(Resident.class))).thenReturn(List.of(
                new WaterUsageReadingResponse(1L, "MTR-0001", LocalDate.of(2026, 1, 1),
                        new BigDecimal("100.000"), ReadingSource.MANUAL, BigDecimal.ZERO, Instant.now(), "2026-01"),
                new WaterUsageReadingResponse(2L, "MTR-0001", LocalDate.of(2026, 2, 1),
                        new BigDecimal("120.000"), ReadingSource.MANUAL, new BigDecimal("20.000"), Instant.now(), "2026-01")));

        mockMvc.perform(get("/api/resident/usage-history").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].consumption").value(20.0));
    }

    @Test
    void usageHistoryReturns200EmptyList() throws Exception {
        when(waterUsageReadingService.listForResident(any(Resident.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/resident/usage-history").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void usageHistoryIsForbiddenForAdmin() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Admin").email("admin@example.com").build();

        mockMvc.perform(get("/api/resident/usage-history").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void usageComparisonReturns200WithData() throws Exception {
        when(waterUsageReadingService.getComparisonForResident(any(Resident.class))).thenReturn(List.of(
                new ConsumptionComparisonPoint("2026-02", new BigDecimal("10.000"), new BigDecimal("30.000"), new BigDecimal("15.000"))));

        mockMvc.perform(get("/api/resident/usage-comparison").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].month").value("2026-02"))
                .andExpect(jsonPath("$[0].apartmentAverage").value(30.0))
                .andExpect(jsonPath("$[0].similarSizedAverage").value(15.0));
    }

    @Test
    void usageComparisonIsForbiddenForAdmin() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Admin").email("admin@example.com").build();

        mockMvc.perform(get("/api/resident/usage-comparison").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }
}
