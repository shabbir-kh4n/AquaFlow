package com.hydroledger.backend.service;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hydroledger.backend.dto.AuthResponse;
import com.hydroledger.backend.dto.LoginRequest;
import com.hydroledger.backend.dto.RegisterRequest;
import com.hydroledger.backend.dto.SetPasswordInfoResponse;
import com.hydroledger.backend.dto.SetPasswordRequest;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.PasswordSetupToken;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.Role;
import com.hydroledger.backend.entity.SystemAdmin;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.PasswordSetupTokenRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;
import com.hydroledger.backend.security.JwtService;
import com.hydroledger.backend.security.ResidentPrincipal;
import com.hydroledger.backend.security.SystemAdminPrincipal;
import com.hydroledger.backend.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final CommunityAdminRepository communityAdminRepository;
    private final ResidentRepository residentRepository;
    private final SystemAdminRepository systemAdminRepository;
    private final PasswordSetupTokenRepository passwordSetupTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final ApartmentService apartmentService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.role() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Self-registration is only available for community admins. Residents are added by their community admin.");
        }

        if (communityAdminRepository.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        CommunityAdmin admin = CommunityAdmin.builder()
                .fullName(request.fullName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .unitNumber(request.unitNumber())
                .build();

        communityAdminRepository.save(admin);
        Apartment apartment = apartmentService.getOrCreate(admin, request.communityName());

        String token = jwtService.generateToken(new UserPrincipal(admin));
        return new AuthResponse(token, admin.getId(), admin.getFullName(), admin.getEmail(), Role.ADMIN,
                apartment.getName(), null, null);
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException | DisabledException | LockedException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        var admin = communityAdminRepository.findByEmail(request.email());
        if (admin.isPresent()) {
            CommunityAdmin found = admin.get();
            Apartment apartment = apartmentService.getOrCreate(found);
            String token = jwtService.generateToken(new UserPrincipal(found));
            return new AuthResponse(token, found.getId(), found.getFullName(), found.getEmail(), Role.ADMIN,
                    apartment.getName(), null, null);
        }

        var resident = residentRepository.findByEmailAndPasswordSetTrue(request.email());
        if (resident.isPresent()) {
            Resident found = resident.get();
            Apartment apartment = apartmentService.getOrCreate(found.getCreatedBy());
            String token = jwtService.generateToken(new ResidentPrincipal(found));
            return new AuthResponse(token, found.getId(), found.getFullName(), found.getEmail(), Role.RESIDENT,
                    apartment.getName(), found.getHouseNumber(), found.getMeterNumber());
        }

        SystemAdmin sysAdmin = systemAdminRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        String token = jwtService.generateToken(new SystemAdminPrincipal(sysAdmin));
        return new AuthResponse(token, sysAdmin.getId(), sysAdmin.getFullName(), sysAdmin.getEmail(),
                Role.SYSTEM_ADMIN, null, null, null);
    }

    public SetPasswordInfoResponse getSetPasswordInfo(String token) {
        Resident resident = resolveValidToken(token).getResident();
        return new SetPasswordInfoResponse(resident.getFullName(), resident.getEmail());
    }

    @Transactional
    public AuthResponse setPassword(SetPasswordRequest request) {
        PasswordSetupToken setupToken = resolveValidToken(request.token());
        Resident resident = setupToken.getResident();

        resident.setPassword(passwordEncoder.encode(request.password()));
        resident.setPasswordSet(true);
        setupToken.setUsed(true);

        Apartment apartment = apartmentService.getOrCreate(resident.getCreatedBy());
        String jwt = jwtService.generateToken(new ResidentPrincipal(resident));
        return new AuthResponse(jwt, resident.getId(), resident.getFullName(), resident.getEmail(), Role.RESIDENT,
                apartment.getName(), resident.getHouseNumber(), resident.getMeterNumber());
    }

    private PasswordSetupToken resolveValidToken(String token) {
        PasswordSetupToken setupToken = passwordSetupTokenRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This link is invalid or has expired"));

        if (setupToken.isUsed() || setupToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.GONE, "This link is invalid or has expired");
        }

        return setupToken;
    }
}
