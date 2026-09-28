package com.hydroledger.backend.announcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.hydroledger.backend.dto.AnnouncementRequest;
import com.hydroledger.backend.dto.AnnouncementResponse;
import com.hydroledger.backend.entity.Announcement;
import com.hydroledger.backend.entity.AnnouncementCategory;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.AnnouncementRepository;
import com.hydroledger.backend.repository.ApartmentRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.service.AnnouncementService;
import com.hydroledger.backend.service.PushNotificationService;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTests {

    @Mock
    private AnnouncementRepository announcementRepository;

    @Mock
    private ApartmentRepository apartmentRepository;

    @Mock
    private ResidentRepository residentRepository;

    @Mock
    private PushNotificationService pushNotificationService;

    @InjectMocks
    private AnnouncementService announcementService;

    private final CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").build();
    private final Apartment apartment = Apartment.builder().id(10L).name("Sunrise Towers").admin(admin).build();
    private final Resident resident = Resident.builder().id(5L).fullName("Res").email("res@example.com").createdBy(admin).build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(announcementService, "frontendUrl", "http://localhost:5175");
    }

    private Announcement announcement(long id, boolean pinned, Instant createdAt, Instant expiresAt) {
        return Announcement.builder().id(id).apartment(apartment).title("Title " + id).body("Body " + id)
                .category(AnnouncementCategory.GENERAL).pinned(pinned).createdAt(createdAt).updatedAt(createdAt)
                .expiresAt(expiresAt).build();
    }

    private AnnouncementRequest request(Instant expiresAt) {
        return new AnnouncementRequest("  Water shutdown  ", " Tank cleaning on Sunday. ", AnnouncementCategory.WATER_SUPPLY,
                true, expiresAt);
    }

    @Test
    void createSavesTrimmedAnnouncementAndPushesToEveryResident() {
        Resident other = Resident.builder().id(6L).fullName("Other").email("o@example.com").createdBy(admin).build();
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(inv -> {
            Announcement a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });
        when(residentRepository.findByCreatedByOrderByCreatedAtDesc(admin)).thenReturn(List.of(resident, other));

        AnnouncementResponse response = announcementService.create(apartment, request(null));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("Water shutdown");
        assertThat(response.body()).isEqualTo("Tank cleaning on Sunday.");
        assertThat(response.category()).isEqualTo(AnnouncementCategory.WATER_SUPPLY);
        assertThat(response.pinned()).isTrue();
        verify(pushNotificationService).sendAnnouncementPush(eq(resident), eq("Water shutdown"),
                eq("Tank cleaning on Sunday."), eq("http://localhost:5175/resident/notifications"));
        verify(pushNotificationService).sendAnnouncementPush(eq(other), anyString(), anyString(), anyString());
    }

    @Test
    void createStillSucceedsWhenPushThrows() {
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(residentRepository.findByCreatedByOrderByCreatedAtDesc(admin)).thenReturn(List.of(resident));
        doThrow(new RuntimeException("push gateway down")).when(pushNotificationService)
                .sendAnnouncementPush(any(), anyString(), anyString(), anyString());

        AnnouncementResponse response = announcementService.create(apartment, request(null));

        assertThat(response.title()).isEqualTo("Water shutdown");
        verify(announcementRepository).save(any(Announcement.class));
    }

    @Test
    void createRejectsExpiryInThePast() {
        assertThatThrownBy(() -> announcementService.create(apartment, request(Instant.now().minus(1, ChronoUnit.HOURS))))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
        verify(announcementRepository, never()).save(any());
    }

    @Test
    void updateChangesFieldsWithoutSendingPush() {
        Announcement existing = announcement(3L, false, Instant.now(), null);
        when(announcementRepository.findByIdAndApartment(3L, apartment)).thenReturn(Optional.of(existing));
        when(announcementRepository.save(existing)).thenReturn(existing);

        AnnouncementResponse response = announcementService.update(apartment, 3L, request(null));

        assertThat(response.title()).isEqualTo("Water shutdown");
        assertThat(response.pinned()).isTrue();
        assertThat(response.category()).isEqualTo(AnnouncementCategory.WATER_SUPPLY);
        verify(pushNotificationService, never()).sendAnnouncementPush(any(), any(), any(), any());
    }

    @Test
    void updateThrows404ForAnotherApartmentsAnnouncement() {
        when(announcementRepository.findByIdAndApartment(99L, apartment)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> announcementService.update(apartment, 99L, request(null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteRemovesOwnedAnnouncement() {
        Announcement existing = announcement(3L, false, Instant.now(), null);
        when(announcementRepository.findByIdAndApartment(3L, apartment)).thenReturn(Optional.of(existing));

        announcementService.delete(apartment, 3L);

        verify(announcementRepository).delete(existing);
    }

    @Test
    void deleteThrows404ForAnotherApartmentsAnnouncement() {
        when(announcementRepository.findByIdAndApartment(99L, apartment)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> announcementService.delete(apartment, 99L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
        verify(announcementRepository, never()).delete(any());
    }

    @Test
    void adminListOrdersPinnedFirstThenNewestAndFlagsExpired() {
        Instant now = Instant.now();
        Announcement oldPinned = announcement(1L, true, now.minus(10, ChronoUnit.DAYS), null);
        Announcement newest = announcement(2L, false, now, null);
        Announcement expired = announcement(3L, false, now.minus(1, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS));
        when(announcementRepository.findByApartmentOrderByCreatedAtDesc(apartment)).thenReturn(List.of(newest, expired, oldPinned));

        List<AnnouncementResponse> list = announcementService.listForAdmin(apartment);

        assertThat(list).extracting(AnnouncementResponse::id).containsExactly(1L, 2L, 3L);
        assertThat(list.get(2).expired()).isTrue();
        assertThat(list.get(0).expired()).isFalse();
    }

    @Test
    void residentListFiltersExpiredAndOrdersPinnedFirst() {
        Instant now = Instant.now();
        Announcement oldPinned = announcement(1L, true, now.minus(10, ChronoUnit.DAYS), now.plus(5, ChronoUnit.DAYS));
        Announcement newest = announcement(2L, false, now, null);
        Announcement expired = announcement(3L, true, now.minus(1, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS));
        when(apartmentRepository.findByAdmin(admin)).thenReturn(Optional.of(apartment));
        when(announcementRepository.findByApartmentOrderByCreatedAtDesc(apartment)).thenReturn(List.of(newest, expired, oldPinned));

        List<AnnouncementResponse> list = announcementService.listForResident(resident);

        assertThat(list).extracting(AnnouncementResponse::id).containsExactly(1L, 2L);
    }

    @Test
    void residentListIsEmptyWhenNoApartment() {
        when(apartmentRepository.findByAdmin(admin)).thenReturn(Optional.empty());

        assertThat(announcementService.listForResident(resident)).isEmpty();
        verify(announcementRepository, never()).findByApartmentOrderByCreatedAtDesc(any());
    }
}
