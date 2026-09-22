package com.hydroledger.backend.push;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hydroledger.backend.controller.ResidentPushController;
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
import com.hydroledger.backend.service.PushSubscriptionService;

@WebMvcTest(ResidentPushController.class)
@Import(SecurityConfig.class)
class PushSubscriptionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PushSubscriptionService pushSubscriptionService;

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

    private final Resident resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").build();

    private static final String VALID_SUBSCRIBE_BODY = "{\"endpoint\":\"https://push.example.com/abc\","
            + "\"keys\":{\"p256dh\":\"p256dh-value\",\"auth\":\"auth-value\"}}";

    @Test
    void getVapidPublicKeyReturns200ForResident() throws Exception {
        mockMvc.perform(get("/api/resident/push/vapid-public-key").with(user(new ResidentPrincipal(resident))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").exists());
    }

    @Test
    void getVapidPublicKeyIsForbiddenForAdmin() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Admin").email("admin@example.com").build();

        mockMvc.perform(get("/api/resident/push/vapid-public-key").with(user(new UserPrincipal(admin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getVapidPublicKeyIsForbiddenWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/resident/push/vapid-public-key"))
                .andExpect(status().isForbidden());
    }

    @Test
    void subscribeReturns204ForResident() throws Exception {
        mockMvc.perform(post("/api/resident/push/subscribe")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SUBSCRIBE_BODY))
                .andExpect(status().isNoContent());
    }

    @Test
    void subscribeRejectsMissingEndpointWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/resident/push/subscribe")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":{\"p256dh\":\"p256dh-value\",\"auth\":\"auth-value\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsubscribeReturns204ForResident() throws Exception {
        mockMvc.perform(post("/api/resident/push/unsubscribe")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://push.example.com/abc\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void subscribeIsForbiddenForAdmin() throws Exception {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Admin").email("admin@example.com").build();

        mockMvc.perform(post("/api/resident/push/subscribe")
                        .with(user(new UserPrincipal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SUBSCRIBE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void subscribeDelegatesToServiceWithResidentPrincipal() throws Exception {
        mockMvc.perform(post("/api/resident/push/subscribe")
                        .with(user(new ResidentPrincipal(resident)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SUBSCRIBE_BODY))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(pushSubscriptionService).subscribe(org.mockito.ArgumentMatchers.eq(resident), any());
    }
}
