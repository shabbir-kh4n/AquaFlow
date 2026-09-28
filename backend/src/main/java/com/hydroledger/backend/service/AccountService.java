package com.hydroledger.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hydroledger.backend.dto.ChangePasswordRequest;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.SystemAdmin;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.security.SystemAdminPrincipal;
import com.hydroledger.backend.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

/**
 * Account operations shared by all three roles. The principal's entity comes from
 * JwtAuthFilter, outside any transaction, so it is re-read by id before being modified.
 * Existing JWTs stay valid after a password change (there is no session tracking).
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final CommunityAdminRepository communityAdminRepository;
    private final ResidentRepository residentRepository;
    private final SystemAdminRepository systemAdminRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void changePassword(UserDetails principal, ChangePasswordRequest request) {
        switch (principal) {
            case ResidentPrincipal p -> {
                Resident resident = residentRepository.findById(p.getUser().getId())
                        .orElseThrow(AccountService::accountNotFound);
                resident.setPassword(checkAndEncode(resident.getPassword(), request));
                residentRepository.save(resident);
            }
            case UserPrincipal p -> {
                CommunityAdmin admin = communityAdminRepository.findById(p.getUser().getId())
                        .orElseThrow(AccountService::accountNotFound);
                admin.setPassword(checkAndEncode(admin.getPassword(), request));
                communityAdminRepository.save(admin);
            }
            case SystemAdminPrincipal p -> {
                SystemAdmin systemAdmin = systemAdminRepository.findById(p.getUser().getId())
                        .orElseThrow(AccountService::accountNotFound);
                systemAdmin.setPassword(checkAndEncode(systemAdmin.getPassword(), request));
                systemAdminRepository.save(systemAdmin);
            }
            default -> throw new ApiException(HttpStatus.FORBIDDEN, "Unsupported account type");
        }
    }

    private String checkAndEncode(String storedHash, ChangePasswordRequest request) {
        if (storedHash == null || !passwordEncoder.matches(request.currentPassword(), storedHash)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "New password must be different from your current password");
        }
        return passwordEncoder.encode(request.newPassword());
    }

    private static ApiException accountNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Account not found");
    }
}
