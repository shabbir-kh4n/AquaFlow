package com.shabbir.backend.meter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shabbir.backend.controller.WaterMeterController;
import com.shabbir.backend.dto.WaterMeterResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.CommunityAdmin;
import com.shabbir.backend.entity.MeterStatus;
import com.shabbir.backend.entity.Resident;
import com.shabbir.backend.repository.CommunityAdminRepository;
import com.shabbir.backend.repository.ResidentRepository;
import com.shabbir.backend.security.CustomUserDetailsService;
import com.shabbir.backend.security.JwtService;
import com.shabbir.backend.security.ResidentPrincipal;
import com.shabbir.backend.security.SecurityConfig;
import com.shabbir.backend.security.UserPrincipal;
import com.shabbir.backend.service.ApartmentService;
import com.shabbir.backend.service.WaterMeterService;

@WebMvcTest(WaterMeterController.class)
@Import(SecurityConfig.class)
class WaterMeterControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WaterMeterService waterMeterService;

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

    @Test
    void listReturnsMetersForAdmin() throws Exception {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(waterMeterService.list(apartment)).thenReturn(List.of(
                new WaterMeterResponse(1L, "MTR-0001", null, null, null, MeterStatus.ACTIVE, Instant.now())));

        mockMvc.perform(get("/api/admin/meters").with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].meterNumber").value("MTR-0001"));
    }

    @Test
    void createReturns201() throws Exception {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(waterMeterService.create(any(), eq(apartment)))
                .thenReturn(new WaterMeterResponse(1L, "MTR-0001", null, null, null, MeterStatus.ACTIVE, Instant.now()));

        mockMvc.perform(post("/api/admin/meters")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterNumber\":\"MTR-0001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.meterNumber").value("MTR-0001"));
    }

    @Test
    void createRejectsBlankMeterNumber() throws Exception {
        mockMvc.perform(post("/api/admin/meters")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterNumber\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReturnsOk() throws Exception {
        when(apartmentService.getOrCreate(admin)).thenReturn(apartment);
        when(waterMeterService.update(eq("MTR-0001"), any(), eq(apartment)))
                .thenReturn(new WaterMeterResponse(1L, "MTR-0001", null, null, null, MeterStatus.INACTIVE, Instant.now()));

        mockMvc.perform(put("/api/admin/meters/MTR-0001")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meterNumber\":\"MTR-0001\",\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void listIsForbiddenForResident() throws Exception {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();

        mockMvc.perform(get("/api/admin/meters").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
    }
}
