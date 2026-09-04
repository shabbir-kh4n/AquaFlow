package com.shabbir.backend.bulkpurchase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shabbir.backend.controller.BulkWaterPurchaseController;
import com.shabbir.backend.dto.BulkWaterPurchaseResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.CommunityAdmin;
import com.shabbir.backend.entity.Resident;
import com.shabbir.backend.repository.CommunityAdminRepository;
import com.shabbir.backend.repository.ResidentRepository;
import com.shabbir.backend.security.CustomUserDetailsService;
import com.shabbir.backend.security.JwtService;
import com.shabbir.backend.security.ResidentPrincipal;
import com.shabbir.backend.security.SecurityConfig;
import com.shabbir.backend.security.UserPrincipal;
import com.shabbir.backend.service.ApartmentService;
import com.shabbir.backend.service.BulkWaterPurchaseService;

@WebMvcTest(BulkWaterPurchaseController.class)
@Import(SecurityConfig.class)
class BulkWaterPurchaseControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BulkWaterPurchaseService bulkWaterPurchaseService;

    @MockitoBean
    private ApartmentService apartmentService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CommunityAdminRepository communityAdminRepository;

    @MockitoBean
    private ResidentRepository residentRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private final CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();
    private final Apartment apartment = Apartment.builder().id(1L).name("Sunrise Towers").admin(admin).build();

    private static final String VALID_BODY = "{\"purchaseDate\":\"2026-09-01\",\"source\":\"ABC Tankers\","
            + "\"volumeLiters\":5000,\"unitCost\":0.85,\"notes\":\"Weekly delivery\"}";

    @Test
    void createReturns201WithSavedPurchase() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(bulkWaterPurchaseService.create(any(Apartment.class), any())).thenReturn(new BulkWaterPurchaseResponse(
                1L, LocalDate.of(2026, 9, 1), "ABC Tankers", new BigDecimal("5000"), new BigDecimal("0.85"),
                new BigDecimal("4250.00"), "Weekly delivery", null));

        mockMvc.perform(post("/api/admin/bulk-water-purchases")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCost").value(4250.00));
    }

    @Test
    void createRejectsInvalidVolumeWithBadRequest() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);

        mockMvc.perform(post("/api/admin/bulk-water-purchases")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purchaseDate\":\"2026-09-01\",\"source\":\"ABC Tankers\",\"volumeLiters\":0,\"unitCost\":0.85}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listReturns200() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(bulkWaterPurchaseService.list(apartment)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/admin/bulk-water-purchases").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk());
    }

    @Test
    void updateReturns200() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(bulkWaterPurchaseService.update(any(Apartment.class), any(), any())).thenReturn(new BulkWaterPurchaseResponse(
                1L, LocalDate.of(2026, 9, 1), "ABC Tankers", new BigDecimal("5000"), new BigDecimal("0.85"),
                new BigDecimal("4250.00"), "Weekly delivery", null));

        mockMvc.perform(put("/api/admin/bulk-water-purchases/1")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void deleteReturns204() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);

        mockMvc.perform(delete("/api/admin/bulk-water-purchases/1").with(user(new UserPrincipal(admin))))
                .andExpect(status().isNoContent());
    }

    @Test
    void listIsForbiddenForResident() throws Exception {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();

        mockMvc.perform(get("/api/admin/bulk-water-purchases").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
    }
}
