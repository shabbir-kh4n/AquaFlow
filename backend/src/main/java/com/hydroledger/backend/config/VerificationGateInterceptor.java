package com.hydroledger.backend.config;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.VerificationStatus;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.AdminVerificationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Low-privilege mode for community admins whose documents aren't approved yet: every /api/admin/**
 * endpoint is refused except the verification flow itself, the profile/apartment pages, and the
 * read-only lists the dashboard (and topbar search/notification bell) load.
 */
public class VerificationGateInterceptor implements HandlerInterceptor {

    static final String MESSAGE = "Your community is not verified yet. Submit your documents and wait for approval.";

    private final AdminVerificationService verificationService;

    public VerificationGateInterceptor(AdminVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            return true;
        }
        if (isAllowedWhileUnverified(request.getMethod(), request.getRequestURI())) {
            return true;
        }
        CommunityAdmin admin = principal.getUser();
        if (verificationService.statusFor(admin) != VerificationStatus.APPROVED) {
            throw new ApiException(HttpStatus.FORBIDDEN, MESSAGE);
        }
        return true;
    }

    static boolean isAllowedWhileUnverified(String method, String path) {
        if (path.equals("/api/admin/verification") || path.startsWith("/api/admin/verification/")) {
            return true;
        }
        if (path.equals("/api/admin/profile") || path.startsWith("/api/admin/profile/")
                || path.equals("/api/admin/apartment")) {
            return true;
        }
        if (HttpMethod.GET.matches(method)) {
            return path.equals("/api/admin/alerts") || path.equals("/api/admin/residents")
                    || path.equals("/api/admin/usage-readings");
        }
        return false;
    }
}
