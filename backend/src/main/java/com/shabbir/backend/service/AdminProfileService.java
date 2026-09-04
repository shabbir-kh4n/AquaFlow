package com.shabbir.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shabbir.backend.dto.AdminProfileRequest;
import com.shabbir.backend.dto.AdminProfileResponse;
import com.shabbir.backend.entity.CommunityAdmin;
import com.shabbir.backend.repository.CommunityAdminRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminProfileService {

    private final CommunityAdminRepository communityAdminRepository;

    public AdminProfileResponse get(CommunityAdmin admin) {
        return AdminProfileResponse.from(admin);
    }

    @Transactional
    public AdminProfileResponse update(AdminProfileRequest request, CommunityAdmin admin) {
        admin.setFullName(request.fullName());
        admin.setPhone(request.phone());
        communityAdminRepository.save(admin);
        return AdminProfileResponse.from(admin);
    }
}
