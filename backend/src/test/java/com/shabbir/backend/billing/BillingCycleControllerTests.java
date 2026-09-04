package com.shabbir.backend.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shabbir.backend.controller.BillingCycleController;
import com.shabbir.backend.dto.BillingCycleResponse;
import com.shabbir.backend.dto.InvoiceResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.BillingCycleStatus;
import com.shabbir.backend.entity.CommunityAdmin;
import com.shabbir.backend.entity.Resident;
import com.shabbir.backend.exception.ApiException;
import com.shabbir.backend.repository.CommunityAdminRepository;
import com.shabbir.backend.repository.ResidentRepository;
import com.shabbir.backend.repository.SystemAdminRepository;
import com.shabbir.backend.security.CustomUserDetailsService;
import com.shabbir.backend.security.JwtService;
import com.shabbir.backend.security.ResidentPrincipal;
import com.shabbir.backend.security.SecurityConfig;
import com.shabbir.backend.security.UserPrincipal;
import com.shabbir.backend.service.ApartmentService;
import com.shabbir.backend.service.BillingCycleService;
import org.springframework.http.HttpStatus;

@WebMvcTest(BillingCycleController.class)
@Import(SecurityConfig.class)
class BillingCycleControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillingCycleService billingCycleService;

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
    void openReturns200WithNewCycle() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(billingCycleService.open(eq(apartment), eq(YearMonth.of(2026, 9)))).thenReturn(
                new BillingCycleResponse(1L, "2026-09", BillingCycleStatus.OPEN, null, null, null, 0, BigDecimal.ZERO));

        mockMvc.perform(post("/api/admin/billing-cycles")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodMonth\":\"2026-09\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void openRejectsBadPeriodMonthFormat() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);

        mockMvc.perform(post("/api/admin/billing-cycles")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodMonth\":\"September 2026\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openPropagatesConflictOnDuplicateMonth() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(billingCycleService.open(eq(apartment), eq(YearMonth.of(2026, 9))))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "A billing cycle already exists for 2026-09"));

        mockMvc.perform(post("/api/admin/billing-cycles")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodMonth\":\"2026-09\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listReturnsCyclesForApartment() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(billingCycleService.list(apartment)).thenReturn(List.of(
                new BillingCycleResponse(1L, "2026-09", BillingCycleStatus.OPEN, null, null, null, 0, BigDecimal.ZERO)));

        mockMvc.perform(get("/api/admin/billing-cycles").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].periodMonth").value("2026-09"));
    }

    @Test
    void listIsForbiddenForResident() throws Exception {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();

        mockMvc.perform(get("/api/admin/billing-cycles").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
    }

    @Test
    void setInvoicePaidReturns200WithUpdatedInvoice() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(billingCycleService.setInvoicePaid(eq(apartment), eq(5L), eq(true))).thenReturn(
                new InvoiceResponse(5L, 1L, "A-101", "Jane Resident", "MTR-0001", "2026-09",
                        new BigDecimal("10"), new BigDecimal("50.00"), new BigDecimal("50.00"), BigDecimal.ZERO,
                        null, true, null, List.of(), List.of()));

        mockMvc.perform(put("/api/admin/billing-cycles/invoices/5/payment-status")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paid\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(true));
    }

    @Test
    void setInvoicePaidIsForbiddenForResident() throws Exception {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();

        mockMvc.perform(put("/api/admin/billing-cycles/invoices/5/payment-status")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paid\":true}"))
                .andExpect(status().isForbidden());
    }
}
