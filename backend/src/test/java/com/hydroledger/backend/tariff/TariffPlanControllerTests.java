package com.hydroledger.backend.tariff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hydroledger.backend.controller.TariffPlanController;
import com.hydroledger.backend.dto.TariffPlanResponse;
import com.hydroledger.backend.dto.TariffTierResponse;
import com.hydroledger.backend.entity.Apartment;
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
import com.hydroledger.backend.service.ApartmentService;
import com.hydroledger.backend.service.TariffPlanService;

@WebMvcTest(TariffPlanController.class)
@Import(SecurityConfig.class)
class TariffPlanControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TariffPlanService tariffPlanService;

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
    void getReturnsDefaultTariffPlan() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(tariffPlanService.getOrDefault(apartment)).thenReturn(new TariffPlanResponse(null,
                List.of(new TariffTierResponse(1, BigDecimal.ZERO, null, new BigDecimal("5.00"))), false, null));

        mockMvc.perform(get("/api/admin/tariff-plan").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persisted").value(false))
                .andExpect(jsonPath("$.tiers[0].ratePerUnit").value(5.00));
    }

    @Test
    void saveReturns200WithSavedPlan() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(tariffPlanService.save(any(Apartment.class), any())).thenReturn(new TariffPlanResponse(1L,
                List.of(new TariffTierResponse(1, BigDecimal.ZERO, null, new BigDecimal("6.00"))), true, null));

        mockMvc.perform(put("/api/admin/tariff-plan")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tiers\":[{\"minUnits\":0,\"maxUnits\":null,\"ratePerUnit\":6.00}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persisted").value(true));
    }

    @Test
    void saveRejectsEmptyTiersWithBadRequest() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);

        mockMvc.perform(put("/api/admin/tariff-plan")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tiers\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getIsForbiddenForResident() throws Exception {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();

        mockMvc.perform(get("/api/admin/tariff-plan").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
    }
}
