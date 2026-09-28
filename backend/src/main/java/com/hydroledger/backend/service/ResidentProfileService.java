package com.hydroledger.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hydroledger.backend.dto.ResidentProfileResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.Role;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.ResidentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read-only resident account details (the community admin manages the resident record) plus
 * the resident's own avatar choice.
 */
@Service
@RequiredArgsConstructor
public class ResidentProfileService {

    private final ResidentRepository residentRepository;
    private final ApartmentService apartmentService;

    @Transactional(readOnly = true)
    public ResidentProfileResponse get(Resident principalResident) {
        return toResponse(load(principalResident));
    }

    @Transactional
    public ResidentProfileResponse updateAvatar(Resident principalResident, String avatarKey) {
        String validated = AvatarKeys.validate(Role.RESIDENT, avatarKey);
        Resident resident = load(principalResident);
        resident.setAvatarKey(validated);
        residentRepository.save(resident);
        return toResponse(resident);
    }

    private Resident load(Resident principalResident) {
        return residentRepository.findById(principalResident.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private ResidentProfileResponse toResponse(Resident resident) {
        Apartment apartment = apartmentService.getOrCreate(resident.getCreatedBy());
        Household household = resident.getHousehold();
        return new ResidentProfileResponse(
                resident.getId(),
                resident.getFullName(),
                resident.getEmail(),
                resident.getPhone(),
                resident.getHouseNumber(),
                resident.getMeterNumber(),
                apartment != null ? apartment.getName() : null,
                household != null ? household.getFlatSizeSqft() : null,
                resident.getDateOfJoining(),
                resident.getCreatedAt(),
                resident.getAvatarKey()
        );
    }
}
