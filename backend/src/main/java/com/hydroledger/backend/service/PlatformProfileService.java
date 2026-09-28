package com.hydroledger.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hydroledger.backend.dto.PlatformProfileResponse;
import com.hydroledger.backend.entity.Role;
import com.hydroledger.backend.entity.SystemAdmin;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.SystemAdminRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlatformProfileService {

    private final SystemAdminRepository systemAdminRepository;

    public PlatformProfileResponse get(SystemAdmin systemAdmin) {
        return PlatformProfileResponse.from(systemAdmin);
    }

    @Transactional
    public PlatformProfileResponse updateAvatar(SystemAdmin principalAdmin, String avatarKey) {
        String validated = AvatarKeys.validate(Role.SYSTEM_ADMIN, avatarKey);
        SystemAdmin systemAdmin = systemAdminRepository.findById(principalAdmin.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        systemAdmin.setAvatarKey(validated);
        systemAdminRepository.save(systemAdmin);
        return PlatformProfileResponse.from(systemAdmin);
    }
}
