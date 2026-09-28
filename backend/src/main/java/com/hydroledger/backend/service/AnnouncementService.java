package com.hydroledger.backend.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hydroledger.backend.dto.AnnouncementRequest;
import com.hydroledger.backend.dto.AnnouncementResponse;
import com.hydroledger.backend.entity.Announcement;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.AnnouncementRepository;
import com.hydroledger.backend.repository.ApartmentRepository;
import com.hydroledger.backend.repository.ResidentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementService.class);

    // Pinned first, then newest first - same order for the admin list and the resident feed.
    private static final Comparator<Announcement> DISPLAY_ORDER = Comparator
            .comparing(Announcement::isPinned).reversed()
            .thenComparing(Announcement::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

    private final AnnouncementRepository announcementRepository;
    private final ApartmentRepository apartmentRepository;
    private final ResidentRepository residentRepository;
    private final PushNotificationService pushNotificationService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public List<AnnouncementResponse> listForAdmin(Apartment apartment) {
        Instant now = Instant.now();
        return announcementRepository.findByApartmentOrderByCreatedAtDesc(apartment).stream()
                .sorted(DISPLAY_ORDER)
                .map(a -> toResponse(a, now))
                .toList();
    }

    // Deliberately not @Transactional: the save commits in the repository's own transaction, and
    // the push fan-out afterwards is best-effort - a push failure must never undo the announcement.
    public AnnouncementResponse create(Apartment apartment, AnnouncementRequest request) {
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Expiry must be in the future");
        }

        Announcement announcement = Announcement.builder()
                .apartment(apartment)
                .title(request.title().trim())
                .body(request.body().trim())
                .category(request.category())
                .pinned(request.isPinned())
                .expiresAt(request.expiresAt())
                .build();

        Announcement saved = announcementRepository.save(announcement);
        notifyResidents(apartment, saved);
        return toResponse(saved, Instant.now());
    }

    @Transactional
    public AnnouncementResponse update(Apartment apartment, Long id, AnnouncementRequest request) {
        Announcement announcement = requireOwned(apartment, id);
        announcement.setTitle(request.title().trim());
        announcement.setBody(request.body().trim());
        announcement.setCategory(request.category());
        announcement.setPinned(request.isPinned());
        announcement.setExpiresAt(request.expiresAt());
        // Edits never re-notify residents - only the initial publish sends a push.
        return toResponse(announcementRepository.save(announcement), Instant.now());
    }

    @Transactional
    public void delete(Apartment apartment, Long id) {
        announcementRepository.delete(requireOwned(apartment, id));
    }

    public List<AnnouncementResponse> listForResident(Resident resident) {
        // resident.getCreatedBy() may be a detached lazy proxy from JWT authentication - it's only
        // used as a query parameter here (identifier only), so it never needs initializing.
        Optional<Apartment> apartment = apartmentRepository.findByAdmin(resident.getCreatedBy());
        if (apartment.isEmpty()) {
            return List.of();
        }

        Instant now = Instant.now();
        return announcementRepository.findByApartmentOrderByCreatedAtDesc(apartment.get()).stream()
                .filter(a -> !isExpired(a, now))
                .sorted(DISPLAY_ORDER)
                .map(a -> toResponse(a, now))
                .toList();
    }

    private void notifyResidents(Apartment apartment, Announcement announcement) {
        try {
            String link = frontendUrl + "/resident/notifications";
            List<Resident> residents = residentRepository.findByCreatedByOrderByCreatedAtDesc(apartment.getAdmin());
            for (Resident resident : residents) {
                pushNotificationService.sendAnnouncementPush(resident, announcement.getTitle(), announcement.getBody(), link);
            }
        } catch (Exception ex) {
            log.warn("Failed to send announcement push for announcement {}: {}", announcement.getId(), ex.getMessage());
        }
    }

    private Announcement requireOwned(Apartment apartment, Long id) {
        return announcementRepository.findByIdAndApartment(id, apartment)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Announcement not found"));
    }

    private boolean isExpired(Announcement announcement, Instant now) {
        return announcement.getExpiresAt() != null && !announcement.getExpiresAt().isAfter(now);
    }

    private AnnouncementResponse toResponse(Announcement announcement, Instant now) {
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getCategory(),
                announcement.isPinned(),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt(),
                announcement.getExpiresAt(),
                isExpired(announcement, now)
        );
    }
}
