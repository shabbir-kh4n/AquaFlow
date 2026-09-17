package com.hydroledger.backend.adminprofile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hydroledger.backend.controller.AdminProfileController;
import com.hydroledger.backend.dto.AdminProfileResponse;
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
import com.hydroledger.backend.service.AdminProfileService;

@WebMvcTest(AdminProfileController.class)
@Import(SecurityConfig.class)
class AdminProfileControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminProfileService adminProfileService;

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
    void getReturnsProfileForAuthenticatedAdmin() throws Exception {
        when(adminProfileService.get(any(CommunityAdmin.class)))
                .thenReturn(new AdminProfileResponse(1L, "Jane Admin", "jane@example.com", "9876543210"));

        mockMvc.perform(get("/api/admin/profile").with(user(new UserPrincipal(admin()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Admin"));
    }

    @Test
    void getIsForbiddenForResident() throws Exception {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();

        mockMvc.perform(get("/api/admin/profile").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getIsForbiddenWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/profile"))
                .andExpect(status().isForbidden());
    }

    @Test
    void putUpdatesFullNameAndPhone() throws Exception {
        when(adminProfileService.update(any(), any(CommunityAdmin.class)))
                .thenReturn(new AdminProfileResponse(1L, "New Name", "jane@example.com", "9999999999"));

        mockMvc.perform(put("/api/admin/profile")
                        .with(user(new UserPrincipal(admin())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\",\"phone\":\"9999999999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"));
    }

    @Test
    void putRejectsBlankFullName() throws Exception {
        mockMvc.perform(put("/api/admin/profile")
                        .with(user(new UserPrincipal(admin())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
