package com.hydroledger.backend.usage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hydroledger.backend.controller.WaterUsageReadingController;
import com.hydroledger.backend.dto.CsvUploadResultResponse;
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

@WebMvcTest(WaterUsageReadingController.class)
@Import(SecurityConfig.class)
class WaterUsageReadingControllerTests {

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

    private final CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();

    @Test
    void recordReturns201() throws Exception {
        when(waterUsageReadingService.recordManual(any(), any(CommunityAdmin.class)))
                .thenReturn(new WaterUsageReadingResponse(1L, "MTR-0001", LocalDate.of(2026, 1, 1),
                        new BigDecimal("100.000"), ReadingSource.MANUAL, BigDecimal.ZERO, Instant.now(), "2026-01"));

        mockMvc.perform(post("/api/admin/usage-readings")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterNumber\":\"MTR-0001\",\"readingDate\":\"2026-01-01\",\"readingValue\":100.000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.meterNumber").value("MTR-0001"));
    }

    @Test
    void searchReturnsList() throws Exception {
        when(waterUsageReadingService.search(any(), any(), any(), any(CommunityAdmin.class)))
                .thenReturn(List.of(new WaterUsageReadingResponse(1L, "MTR-0001", LocalDate.of(2026, 1, 1),
                        new BigDecimal("100.000"), ReadingSource.MANUAL, BigDecimal.ZERO, Instant.now(), "2026-01")));

        mockMvc.perform(get("/api/admin/usage-readings").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].meterNumber").value("MTR-0001"));
    }

    @Test
    void bulkCsvUploadReturns200WithPartialResult() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "readings.csv", "text/csv",
                "MTR-0001,2026-01-01,100.000".getBytes());
        when(waterUsageReadingService.bulkUploadCsv(any(), any(CommunityAdmin.class)))
                .thenReturn(new CsvUploadResultResponse(1, 1, 0, List.of()));

        mockMvc.perform(multipart("/api/admin/usage-readings/bulk-csv")
                        .file(file)
                        .with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.successCount").value(1));
    }

    @Test
    void recordIsForbiddenForResident() throws Exception {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();

        mockMvc.perform(post("/api/admin/usage-readings")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterNumber\":\"MTR-0001\",\"readingDate\":\"2026-01-01\",\"readingValue\":100.000}"))
                .andExpect(status().isForbidden());
    }
}
