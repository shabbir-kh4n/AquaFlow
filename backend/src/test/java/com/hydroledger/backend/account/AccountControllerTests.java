package com.hydroledger.backend.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.hydroledger.backend.controller.AccountController;
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
import com.hydroledger.backend.service.AccountService;

@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
class AccountControllerTests {

    private static final String VALID_BODY = "{\"currentPassword\":\"oldPassword1\",\"newPassword\":\"newPassword1\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

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

    private MockHttpServletRequestBuilder changePassword(String body) {
        return post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void residentCanChangePassword() throws Exception {
        Resident resident = Resident.builder().id(2L).fullName("Res").email("res@example.com").build();

        mockMvc.perform(changePassword(VALID_BODY).with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated"));
        verify(accountService).changePassword(argThat(p -> p instanceof ResidentPrincipal), any());
    }

    @Test
    void communityAdminCanChangePassword() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Jane").email("jane@example.com").build();

        mockMvc.perform(changePassword(VALID_BODY).with(user(new UserPrincipal(admin))))
                .andExpect(status().isOk());
        verify(accountService).changePassword(argThat(p -> p instanceof UserPrincipal), any());
    }

    @Test
    void systemAdminCanChangePassword() throws Exception {
        SystemAdmin systemAdmin = SystemAdmin.builder().id(9L).fullName("Root").email("root@hydroledger.local").build();

        mockMvc.perform(changePassword(VALID_BODY).with(user(new SystemAdminPrincipal(systemAdmin))))
                .andExpect(status().isOk());
        verify(accountService).changePassword(argThat(p -> p instanceof SystemAdminPrincipal), any());
    }

    @Test
    void wrongCurrentPasswordReturns400WithMessage() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Jane").email("jane@example.com").build();
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect"))
                .when(accountService).changePassword(any(), any());

        mockMvc.perform(changePassword(VALID_BODY).with(user(new UserPrincipal(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    void tooShortNewPasswordFailsValidation() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Jane").email("jane@example.com").build();

        mockMvc.perform(changePassword("{\"currentPassword\":\"oldPassword1\",\"newPassword\":\"short\"}")
                        .with(user(new UserPrincipal(admin))))
                .andExpect(status().isBadRequest());
        verify(accountService, never()).changePassword(any(), any());
    }

    @Test
    void blankCurrentPasswordFailsValidation() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Jane").email("jane@example.com").build();

        mockMvc.perform(changePassword("{\"currentPassword\":\"\",\"newPassword\":\"newPassword1\"}")
                        .with(user(new UserPrincipal(admin))))
                .andExpect(status().isBadRequest());
        verify(accountService, never()).changePassword(any(), any());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(changePassword(VALID_BODY))
                .andExpect(status().isForbidden());
        verify(accountService, never()).changePassword(any(), any());
    }
}
