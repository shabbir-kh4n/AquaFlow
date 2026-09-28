package com.hydroledger.backend.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hydroledger.backend.controller.AdminReportController;
import com.hydroledger.backend.dto.AdminReportResponse;
import com.hydroledger.backend.dto.AdminReportResponse.AdminReportSummary;
import com.hydroledger.backend.dto.AdminReportResponse.ReportRangeInfo;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;
import com.hydroledger.backend.security.CustomUserDetailsService;
import com.hydroledger.backend.security.JwtService;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.security.SecurityConfig;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.AdminReportPdfService;
import com.hydroledger.backend.service.AdminReportService;
import com.hydroledger.backend.service.ApartmentService;

@WebMvcTest(AdminReportController.class)
@Import(SecurityConfig.class)
class AdminReportControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminReportService adminReportService;

    @MockitoBean
    private AdminReportPdfService adminReportPdfService;

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

    private AdminReportResponse sampleReport() {
        AdminReportSummary summary = new AdminReportSummary(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
        return new AdminReportResponse("Sunrise Towers",
                new ReportRangeInfo("6mo", "2026-04", "2026-09", List.of()), summary, List.of(), List.of(), List.of());
    }

    @Test
    void getReportReturns200ScopedToCallersApartment() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(adminReportService.getReport(eq(apartment), eq("6mo"), isNull(), isNull())).thenReturn(sampleReport());

        mockMvc.perform(get("/api/admin/reports").param("range", "6mo").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apartmentName").value("Sunrise Towers"))
                .andExpect(jsonPath("$.range.key").value("6mo"));
    }

    @Test
    void getReportReturns400ForInvalidRange() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(adminReportService.getReport(eq(apartment), isNull(), eq("2026-05"), eq("2026-01")))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'"));

        mockMvc.perform(get("/api/admin/reports").param("from", "2026-05").param("to", "2026-01")
                        .with(user(new UserPrincipal(admin))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadPdfReturnsPdfAttachment() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        AdminReportResponse report = sampleReport();
        when(adminReportService.getReport(eq(apartment), eq("6mo"), isNull(), isNull())).thenReturn(report);
        when(adminReportPdfService.generate(report)).thenReturn("%PDF-1.4".getBytes());

        mockMvc.perform(get("/api/admin/reports/pdf").param("range", "6mo").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"community-report-2026-04-to-2026-09.pdf\""));
    }

    @Test
    void getReportIsForbiddenForResident() throws Exception {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();

        mockMvc.perform(get("/api/admin/reports").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
        verify(adminReportService, never()).getReport(any(), any(), any(), any());
    }

    @Test
    void getReportRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().is4xxClientError());
    }
}
