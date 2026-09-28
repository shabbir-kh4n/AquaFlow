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

import com.hydroledger.backend.controller.ResidentBillingController;
import com.hydroledger.backend.controller.ResidentReportController;
import com.hydroledger.backend.dto.ResidentReportResponse;
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
import com.hydroledger.backend.service.BillingCycleService;
import com.hydroledger.backend.service.ResidentReportService;
import com.hydroledger.backend.service.TariffPlanService;

// ResidentBillingController is loaded too, to prove /invoices/statement/pdf wins over its /invoices/{id}/pdf.
@WebMvcTest({ResidentReportController.class, ResidentBillingController.class})
@Import(SecurityConfig.class)
class ResidentReportControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private ResidentReportService residentReportService;
    @MockitoBean private BillingCycleService billingCycleService;
    @MockitoBean private TariffPlanService tariffPlanService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private CommunityAdminRepository communityAdminRepository;
    @MockitoBean private ResidentRepository residentRepository;
    @MockitoBean private SystemAdminRepository systemAdminRepository;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private final Resident resident = Resident.builder().id(1L).fullName("Res Ident").email("res@example.com").build();

    @Test
    void reportReturns200() throws Exception {
        ResidentReportResponse report = new ResidentReportResponse(
                new ResidentReportResponse.ReportRange("custom", "2026-03", "2026-04", "2026-01", "2026-02"),
                new ResidentReportResponse.Summary(new BigDecimal("30"), new BigDecimal("160.50"), new BigDecimal("100"),
                        new BigDecimal("60.50"), new BigDecimal("15"), new BigDecimal("20"), new BigDecimal("50.0")),
                List.of(new ResidentReportResponse.MonthRow("2026-03", BigDecimal.TEN, new BigDecimal("100"),
                        new BigDecimal("100"), true, true)));
        when(residentReportService.getReport(any(Resident.class), isNull(), eq("2026-03"), eq("2026-04"))).thenReturn(report);

        mockMvc.perform(get("/api/resident/reports").param("from", "2026-03").param("to", "2026-04")
                        .with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range.key").value("custom"))
                .andExpect(jsonPath("$.summary.outstandingAmount").value(60.5))
                .andExpect(jsonPath("$.months[0].periodMonth").value("2026-03"));
    }

    @Test
    void reportReturns400ForInvalidRange() throws Exception {
        when(residentReportService.getReport(any(Resident.class), isNull(), eq("2026-05"), eq("2026-01")))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'"));

        mockMvc.perform(get("/api/resident/reports").param("from", "2026-05").param("to", "2026-01")
                        .with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportIsForbiddenForAdmin() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Admin").email("admin@example.com").build();

        mockMvc.perform(get("/api/resident/reports").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void statementPdfReturns200WithPdfContentType() throws Exception {
        when(residentReportService.generateStatementPdf(any(Resident.class))).thenReturn(new byte[] {'%', 'P', 'D', 'F'});

        mockMvc.perform(get("/api/resident/invoices/statement/pdf").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        verify(billingCycleService, never()).generateInvoicePdfForResident(any(Resident.class), any());
    }

    @Test
    void statementPdfIsForbiddenForAdmin() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Admin").email("admin@example.com").build();

        mockMvc.perform(get("/api/resident/invoices/statement/pdf").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }
}
