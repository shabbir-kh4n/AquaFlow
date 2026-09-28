package com.hydroledger.backend.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hydroledger.backend.controller.AdminProfileController;
import com.hydroledger.backend.controller.PlatformProfileController;
import com.hydroledger.backend.controller.ResidentProfileController;
import com.hydroledger.backend.dto.AdminProfileResponse;
import com.hydroledger.backend.dto.PlatformProfileResponse;
import com.hydroledger.backend.dto.ResidentProfileResponse;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.SystemAdmin;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;
import com.hydroledger.backend.security.CustomUserDetailsService;
import com.hydroledger.backend.security.JwtService;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.security.SecurityConfig;
import com.hydroledger.backend.security.SystemAdminPrincipal;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.AdminProfileService;
import com.hydroledger.backend.service.PlatformProfileService;
import com.hydroledger.backend.service.ResidentProfileService;

@WebMvcTest({ResidentProfileController.class, PlatformProfileController.class, AdminProfileController.class})
@Import(SecurityConfig.class)
class ProfileAvatarControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResidentProfileService residentProfileService;

    @MockitoBean
    private PlatformProfileService platformProfileService;

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

    private final Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();
    private final CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Jane").email("jane@example.com").build();
    private final SystemAdmin systemAdmin = SystemAdmin.builder().id(9L).fullName("Root").email("root@hydroledger.local").build();

    private ResidentProfileResponse residentProfile(String avatarKey) {
        return new ResidentProfileResponse(2L, "Res", "res@example.com", null, "A-101", "MTR-0001",
                "Sunrise Towers", null, null, null, avatarKey);
    }

    @Test
    void residentGetsOwnProfile() throws Exception {
        when(residentProfileService.get(any(Resident.class))).thenReturn(residentProfile(null));

        mockMvc.perform(get("/api/resident/profile").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.houseNumber").value("A-101"))
                .andExpect(jsonPath("$.communityName").value("Sunrise Towers"));
    }

    @Test
    void residentProfileIsForbiddenForAdmin() throws Exception {
        mockMvc.perform(get("/api/resident/profile").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void residentUpdatesAvatar() throws Exception {
        when(residentProfileService.updateAvatar(any(Resident.class), eq("resident-04")))
                .thenReturn(residentProfile("resident-04"));

        mockMvc.perform(put("/api/resident/profile/avatar")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarKey\":\"resident-04\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarKey").value("resident-04"));
    }

    @Test
    void residentWrongRoleAvatarReturns400() throws Exception {
        when(residentProfileService.updateAvatar(any(Resident.class), eq("admin-04")))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "That avatar isn't available for your account."));

        mockMvc.perform(put("/api/resident/profile/avatar")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarKey\":\"admin-04\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overlongAvatarKeyFailsValidation() throws Exception {
        mockMvc.perform(put("/api/resident/profile/avatar")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarKey\":\"resident-0123456789012345678901234567890\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void residentNullAvatarIsPassedThroughToClear() throws Exception {
        when(residentProfileService.updateAvatar(any(Resident.class), isNull())).thenReturn(residentProfile(null));

        mockMvc.perform(put("/api/resident/profile/avatar")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarKey\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarKey").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void adminUpdatesAvatar() throws Exception {
        when(adminProfileService.updateAvatar(eq("admin-02"), any(CommunityAdmin.class)))
                .thenReturn(new AdminProfileResponse(1L, "Jane", "jane@example.com", null, "admin-02"));

        mockMvc.perform(put("/api/admin/profile/avatar")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarKey\":\"admin-02\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarKey").value("admin-02"));
    }

    @Test
    void adminAvatarEndpointForbiddenForResident() throws Exception {
        mockMvc.perform(put("/api/admin/profile/avatar")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarKey\":\"admin-02\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemAdminGetsAndUpdatesProfile() throws Exception {
        when(platformProfileService.get(any(SystemAdmin.class)))
                .thenReturn(new PlatformProfileResponse(9L, "Root", "root@hydroledger.local", null));
        when(platformProfileService.updateAvatar(any(SystemAdmin.class), eq("system-09")))
                .thenReturn(new PlatformProfileResponse(9L, "Root", "root@hydroledger.local", "system-09"));

        mockMvc.perform(get("/api/platform/profile").with(user(new SystemAdminPrincipal(systemAdmin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("root@hydroledger.local"));

        mockMvc.perform(put("/api/platform/profile")
                        .with(user(new SystemAdminPrincipal(systemAdmin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarKey\":\"system-09\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarKey").value("system-09"));
    }

    @Test
    void platformProfileForbiddenForCommunityAdmin() throws Exception {
        mockMvc.perform(get("/api/platform/profile").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }
}
