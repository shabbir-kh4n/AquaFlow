package com.hydroledger.backend.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hydroledger.backend.controller.ResidentBillingController;
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
import com.hydroledger.backend.service.TariffPlanService;

@WebMvcTest(ResidentBillingController.class)
@Import(SecurityConfig.class)
class ResidentBillingControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TariffPlanService tariffPlanService;

    @MockitoBean
    private BillingCycleService billingCycleService;

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
    void downloadInvoicePdfReturns200WithPdfContentType() throws Exception {
        when(billingCycleService.generateInvoicePdfForResident(eq(resident), eq(5L))).thenReturn(new byte[] {1, 2, 3});

        mockMvc.perform(get("/api/resident/invoices/5/pdf").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void downloadInvoicePdfPropagatesNotFoundForSomeoneElsesInvoice() throws Exception {
        when(billingCycleService.generateInvoicePdfForResident(any(Resident.class), eq(999L)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));

        mockMvc.perform(get("/api/resident/invoices/999/pdf").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadInvoicePdfIsForbiddenForAdmin() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Admin").email("admin@example.com").build();

        mockMvc.perform(get("/api/resident/invoices/5/pdf").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }
}
