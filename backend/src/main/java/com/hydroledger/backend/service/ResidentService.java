package com.hydroledger.backend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hydroledger.backend.dto.ResidentRequest;
import com.hydroledger.backend.dto.ResidentResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.PasswordSetupToken;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.PasswordSetupTokenRepository;
import com.hydroledger.backend.repository.ResidentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResidentService {

    private static final int SET_PASSWORD_TOKEN_VALIDITY_HOURS = 24;

    private final ResidentRepository residentRepository;
    private final PasswordSetupTokenRepository passwordSetupTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ApartmentService apartmentService;
    private final HouseholdService householdService;
    private final WaterMeterService waterMeterService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional
    public ResidentResponse create(ResidentRequest request, CommunityAdmin admin) {
        if (residentRepository.existsByEmailAndCreatedBy(request.email(), admin)) {
            throw new ApiException(HttpStatus.CONFLICT, "A resident with this email already exists in your community");
        }

        if (residentRepository.existsByHouseNumberAndCreatedBy(request.houseNumber(), admin)) {
            throw new ApiException(HttpStatus.CONFLICT, "This house number is already assigned to another resident");
        }

        if (residentRepository.existsByMeterNumberAndCreatedBy(request.meterNumber(), admin)) {
            throw new ApiException(HttpStatus.CONFLICT, "This meter number is already assigned to another resident");
        }

        if (request.bypassSetPassword() && (request.password() == null || request.password().length() < 8)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }

        Apartment apartment = apartmentService.getOrCreate(admin);
        Household household = householdService.findOrCreate(apartment, request.houseNumber(), request.flatSizeSqft());

        Resident resident = Resident.builder()
                .fullName(request.fullName())
                .email(request.email())
                .phone(request.phone())
                .houseNumber(request.houseNumber())
                .meterNumber(request.meterNumber())
                .governmentId(request.governmentId())
                .gender(request.gender())
                .dateOfBirth(request.dateOfBirth())
                .dateOfJoining(request.dateOfJoining())
                .createdBy(admin)
                .household(household)
                .build();

        waterMeterService.findOrCreate(apartment, request.meterNumber(), household);

        if (request.bypassSetPassword()) {
            resident.setPassword(passwordEncoder.encode(request.password()));
            resident.setPasswordSet(true);
            residentRepository.save(resident);
        } else {
            residentRepository.save(resident);
            sendSetPasswordLink(resident);
        }

        return ResidentResponse.from(resident);
    }

    private void sendSetPasswordLink(Resident resident) {
        PasswordSetupToken setupToken = PasswordSetupToken.builder()
                .token(UUID.randomUUID().toString())
                .resident(resident)
                .expiresAt(Instant.now().plus(SET_PASSWORD_TOKEN_VALIDITY_HOURS, ChronoUnit.HOURS))
                .build();

        passwordSetupTokenRepository.save(setupToken);

        String link = frontendUrl + "/set-password/" + setupToken.getToken();
        emailService.sendSetPasswordEmail(resident, link);
    }

    public List<ResidentResponse> listForAdmin(CommunityAdmin admin) {
        return residentRepository.findByCreatedByOrderByCreatedAtDesc(admin).stream()
                .map(ResidentResponse::from)
                .toList();
    }

    @Transactional
    public ResidentResponse update(Long id, ResidentRequest request, CommunityAdmin admin) {
        Resident resident = residentRepository.findByIdAndCreatedBy(id, admin)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Resident not found"));

        if (residentRepository.existsByHouseNumberAndCreatedByAndIdNot(request.houseNumber(), admin, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "This house number is already assigned to another resident");
        }

        if (residentRepository.existsByMeterNumberAndCreatedByAndIdNot(request.meterNumber(), admin, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "This meter number is already assigned to another resident");
        }

        Apartment apartment = apartmentService.getOrCreate(admin);
        Household household = householdService.findOrCreate(apartment, request.houseNumber(), request.flatSizeSqft());

        resident.setFullName(request.fullName());
        resident.setEmail(request.email());
        resident.setPhone(request.phone());
        resident.setHouseNumber(request.houseNumber());
        resident.setMeterNumber(request.meterNumber());
        resident.setGovernmentId(request.governmentId());
        resident.setGender(request.gender());
        resident.setDateOfBirth(request.dateOfBirth());
        resident.setDateOfJoining(request.dateOfJoining());
        resident.setHousehold(household);

        waterMeterService.findOrCreate(apartment, request.meterNumber(), household);

        return ResidentResponse.from(resident);
    }

    @Transactional
    public void delete(Long id, CommunityAdmin admin) {
        Resident resident = residentRepository.findByIdAndCreatedBy(id, admin)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Resident not found"));

        passwordSetupTokenRepository.deleteByResident(resident);
        residentRepository.delete(resident);
    }
}
