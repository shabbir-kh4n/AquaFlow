package com.hydroledger.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hydroledger.backend.dto.AdminProfileResponse;
import com.hydroledger.backend.dto.PlatformProfileResponse;
import com.hydroledger.backend.dto.ResidentProfileResponse;
import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.Household;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.entity.SystemAdmin;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.repository.ResidentRepository;
import com.hydroledger.backend.repository.SystemAdminRepository;
import com.hydroledger.backend.service.AdminProfileService;
import com.hydroledger.backend.service.ApartmentService;
import com.hydroledger.backend.service.PlatformProfileService;
import com.hydroledger.backend.service.ResidentProfileService;

@ExtendWith(MockitoExtension.class)
class ProfileAvatarServiceTests {

    @Mock
    private ResidentRepository residentRepository;

    @Mock
    private CommunityAdminRepository communityAdminRepository;

    @Mock
    private SystemAdminRepository systemAdminRepository;

    @Mock
    private ApartmentService apartmentService;

    private final CommunityAdmin admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin")
            .email("jane@example.com").build();

    private Resident resident() {
        Household household = Household.builder().id(5L).houseNumber("A-101")
                .flatSizeSqft(new BigDecimal("1200.00")).build();
        return Resident.builder().id(2L).fullName("Res Ident").email("res@example.com").phone("9000000000")
                .houseNumber("A-101").meterNumber("MTR-0001").dateOfJoining("2026-01-15")
                .household(household).createdBy(admin).build();
    }

    // --- resident ---

    @Test
    void residentProfileReturnsReadOnlyAccountDetails() {
        Resident resident = resident();
        when(residentRepository.findById(2L)).thenReturn(Optional.of(resident));
        when(apartmentService.getOrCreate(admin)).thenReturn(Apartment.builder().id(1L).name("Sunrise Towers").build());

        ResidentProfileResponse response = new ResidentProfileService(residentRepository, apartmentService).get(resident);

        assertThat(response.fullName()).isEqualTo("Res Ident");
        assertThat(response.houseNumber()).isEqualTo("A-101");
        assertThat(response.meterNumber()).isEqualTo("MTR-0001");
        assertThat(response.communityName()).isEqualTo("Sunrise Towers");
        assertThat(response.flatSizeSqft()).isEqualByComparingTo("1200");
        assertThat(response.dateOfJoining()).isEqualTo("2026-01-15");
        assertThat(response.avatarKey()).isNull();
    }

    @Test
    void residentCanPickOwnAvatar() {
        Resident resident = resident();
        when(residentRepository.findById(2L)).thenReturn(Optional.of(resident));
        when(apartmentService.getOrCreate(admin)).thenReturn(Apartment.builder().id(1L).name("Sunrise Towers").build());

        ResidentProfileResponse response = new ResidentProfileService(residentRepository, apartmentService)
                .updateAvatar(resident, "resident-07");

        assertThat(response.avatarKey()).isEqualTo("resident-07");
        assertThat(resident.getAvatarKey()).isEqualTo("resident-07");
        verify(residentRepository).save(resident);
    }

    @Test
    void residentCannotPickAdminAvatar() {
        ResidentProfileService service = new ResidentProfileService(residentRepository, apartmentService);

        assertThatThrownBy(() -> service.updateAvatar(resident(), "admin-07")).isInstanceOf(ApiException.class);
        verify(residentRepository, never()).save(any());
    }

    @Test
    void residentNullAvatarClearsIt() {
        Resident resident = resident();
        resident.setAvatarKey("resident-03");
        when(residentRepository.findById(2L)).thenReturn(Optional.of(resident));
        when(apartmentService.getOrCreate(admin)).thenReturn(Apartment.builder().id(1L).name("Sunrise Towers").build());

        ResidentProfileResponse response = new ResidentProfileService(residentRepository, apartmentService)
                .updateAvatar(resident, null);

        assertThat(response.avatarKey()).isNull();
        assertThat(resident.getAvatarKey()).isNull();
    }

    // --- community admin ---

    @Test
    void adminCanPickOwnAvatar() {
        AdminProfileResponse response = new AdminProfileService(communityAdminRepository).updateAvatar("admin-03", admin);

        assertThat(response.avatarKey()).isEqualTo("admin-03");
        verify(communityAdminRepository).save(admin);
    }

    @Test
    void adminCannotPickSystemAvatarOrMalformedKey() {
        AdminProfileService service = new AdminProfileService(communityAdminRepository);

        assertThatThrownBy(() -> service.updateAvatar("system-03", admin)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.updateAvatar("admin-99", admin)).isInstanceOf(ApiException.class);
        verify(communityAdminRepository, never()).save(any());
    }

    @Test
    void adminNullAvatarClearsIt() {
        admin.setAvatarKey("admin-05");

        AdminProfileResponse response = new AdminProfileService(communityAdminRepository).updateAvatar(null, admin);

        assertThat(response.avatarKey()).isNull();
    }

    // --- system admin ---

    @Test
    void systemAdminCanPickOwnAvatar() {
        SystemAdmin systemAdmin = SystemAdmin.builder().id(9L).fullName("Root").email("root@hydroledger.local").build();
        when(systemAdminRepository.findById(9L)).thenReturn(Optional.of(systemAdmin));

        PlatformProfileResponse response = new PlatformProfileService(systemAdminRepository)
                .updateAvatar(systemAdmin, "system-12");

        assertThat(response.avatarKey()).isEqualTo("system-12");
        assertThat(response.email()).isEqualTo("root@hydroledger.local");
        verify(systemAdminRepository).save(systemAdmin);
    }

    @Test
    void systemAdminCannotPickResidentAvatar() {
        SystemAdmin systemAdmin = SystemAdmin.builder().id(9L).fullName("Root").email("root@hydroledger.local").build();
        PlatformProfileService service = new PlatformProfileService(systemAdminRepository);

        assertThatThrownBy(() -> service.updateAvatar(systemAdmin, "resident-01")).isInstanceOf(ApiException.class);
        verify(systemAdminRepository, never()).save(any());
    }
}
