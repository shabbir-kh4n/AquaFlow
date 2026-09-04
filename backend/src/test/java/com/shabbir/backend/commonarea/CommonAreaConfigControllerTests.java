package com.shabbir.backend.commonarea;

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

import com.shabbir.backend.controller.CommonAreaConfigController;
import com.shabbir.backend.dto.CommonAreaConfigResponse;
import com.shabbir.backend.dto.CommonAreaCostItemResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.CommonAreaDistributionMethod;
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
import com.shabbir.backend.service.ApartmentService;
import com.shabbir.backend.service.CommonAreaConfigService;

@WebMvcTest(CommonAreaConfigController.class)
@Import(SecurityConfig.class)
class CommonAreaConfigControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommonAreaConfigService commonAreaConfigService;

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
    void getReturnsDefaultConfig() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(commonAreaConfigService.getOrDefault(apartment)).thenReturn(
                new CommonAreaConfigResponse(null, CommonAreaDistributionMethod.CONSUMPTION, List.of(), false, null));

        mockMvc.perform(get("/api/admin/common-area-config").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persisted").value(false));
    }

    @Test
    void saveReturns200WithSavedConfig() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);
        when(commonAreaConfigService.save(any(Apartment.class), any())).thenReturn(new CommonAreaConfigResponse(1L,
                CommonAreaDistributionMethod.FLOOR_AREA,
                List.of(new CommonAreaCostItemResponse(1L, "Pool Maintenance", new BigDecimal("22000"), true)), true, null));

        mockMvc.perform(put("/api/admin/common-area-config")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distributionMethod\":\"FLOOR_AREA\",\"costItems\":"
                                + "[{\"label\":\"Pool Maintenance\",\"monthlyAmount\":22000,\"active\":true}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persisted").value(true));
    }

    @Test
    void saveRejectsEmptyCostItemsWithBadRequest() throws Exception {
        when(apartmentService.getOrCreate(any(CommunityAdmin.class))).thenReturn(apartment);

        mockMvc.perform(put("/api/admin/common-area-config")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distributionMethod\":\"CONSUMPTION\",\"costItems\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getIsForbiddenForResident() throws Exception {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();

        mockMvc.perform(get("/api/admin/common-area-config").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
    }
}
