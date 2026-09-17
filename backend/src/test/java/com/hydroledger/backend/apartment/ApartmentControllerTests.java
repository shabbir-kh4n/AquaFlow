package com.hydroledger.backend.apartment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hydroledger.backend.controller.ApartmentController;
import com.hydroledger.backend.dto.ApartmentResponse;
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

@WebMvcTest(ApartmentController.class)
@Import(SecurityConfig.class)
class ApartmentControllerTests {

    @Autowired
    private MockMvc mockMvc;

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

    private CommunityAdmin admin() {
        return CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();
    }

    @Test
    void getReturnsApartmentForAuthenticatedAdmin() throws Exception {
        when(apartmentService.getForAdmin(any(CommunityAdmin.class)))
                .thenReturn(new ApartmentResponse(1L, "Sunrise Towers", "123 Main St", "Municipal Supply", Instant.now()));

        mockMvc.perform(get("/api/admin/apartment").with(user(new UserPrincipal(admin()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sunrise Towers"));
    }

    @Test
    void getIsForbiddenForResident() throws Exception {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();

        mockMvc.perform(get("/api/admin/apartment").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getIsForbiddenWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/apartment"))
                .andExpect(status().isForbidden());
    }

    @Test
    void putUpdatesApartmentName() throws Exception {
        when(apartmentService.update(any(), any(CommunityAdmin.class)))
                .thenReturn(new ApartmentResponse(1L, "New Name", null, null, Instant.now()));

        mockMvc.perform(put("/api/admin/apartment")
                        .with(user(new UserPrincipal(admin())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void putRejectsBlankName() throws Exception {
        mockMvc.perform(put("/api/admin/apartment")
                        .with(user(new UserPrincipal(admin())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
