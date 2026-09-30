package com.hydroledger.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.hydroledger.backend.config.VerificationGateInterceptor;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.VerificationStatus;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.AdminVerificationService;

class VerificationGateInterceptorTests {

    private final AdminVerificationService service = mock(AdminVerificationService.class);
    private final VerificationGateInterceptor gate = new VerificationGateInterceptor(service);
    private final CommunityAdmin admin = CommunityAdmin.builder().id(5L).fullName("A").email("a@x.com").build();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private boolean call(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        return gate.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    private void signInAsAdmin(VerificationStatus status) {
        UserPrincipal principal = new UserPrincipal(admin);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(service.statusFor(admin)).thenReturn(status);
    }

    @Test
    void unverifiedAdminCanReachVerificationProfileAndDashboardData() {
        signInAsAdmin(VerificationStatus.PENDING);
        assertThat(call("GET", "/api/admin/verification")).isTrue();
        assertThat(call("POST", "/api/admin/verification")).isTrue();
        assertThat(call("GET", "/api/admin/verification/document")).isTrue();
        assertThat(call("GET", "/api/admin/profile")).isTrue();
        assertThat(call("PUT", "/api/admin/apartment")).isTrue();
        assertThat(call("GET", "/api/admin/alerts")).isTrue();
        assertThat(call("GET", "/api/admin/residents")).isTrue();
        assertThat(call("GET", "/api/admin/usage-readings")).isTrue();
    }

    @Test
    void unverifiedAdminIsBlockedFromEverythingElse() {
        for (VerificationStatus status : new VerificationStatus[] {
                VerificationStatus.NOT_SUBMITTED, VerificationStatus.PENDING, VerificationStatus.REJECTED}) {
            signInAsAdmin(status);
            assertThatThrownBy(() -> call("GET", "/api/admin/billing-cycles")).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> call("POST", "/api/admin/residents")).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> call("PUT", "/api/admin/alerts/1/status")).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> call("POST", "/api/admin/chat")).isInstanceOf(ApiException.class);
        }
    }

    @Test
    void approvedAdminIsNotRestricted() {
        signInAsAdmin(VerificationStatus.APPROVED);
        assertThat(call("GET", "/api/admin/billing-cycles")).isTrue();
        assertThat(call("POST", "/api/admin/residents")).isTrue();
    }

    @Test
    void nonAdminPrincipalsAreIgnored() {
        ResidentPrincipal principal = mock(ResidentPrincipal.class);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
        assertThat(call("GET", "/api/admin/billing-cycles")).isTrue();
    }
}
