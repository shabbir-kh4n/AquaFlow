package com.hydroledger.backend.adminprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hydroledger.backend.dto.AdminProfileRequest;
import com.hydroledger.backend.dto.AdminProfileResponse;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.service.AdminProfileService;

@ExtendWith(MockitoExtension.class)
class AdminProfileServiceTests {

    @Mock
    private CommunityAdminRepository communityAdminRepository;

    @InjectMocks
    private AdminProfileService adminProfileService;

    private CommunityAdmin admin;

    @BeforeEach
    void setUp() {
        admin = CommunityAdmin.builder().id(1L).fullName("Jane Admin").email("jane@example.com").phone("9876543210").build();
    }

    @Test
    void getReturnsCurrentAdminDetails() {
        AdminProfileResponse response = adminProfileService.get(admin);

        assertThat(response.fullName()).isEqualTo("Jane Admin");
        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.phone()).isEqualTo("9876543210");
    }

    @Test
    void updateChangesFullNameAndPhoneButNotEmail() {
        AdminProfileResponse response = adminProfileService.update(
                new AdminProfileRequest("Jane Updated", "9999999999"), admin);

        assertThat(response.fullName()).isEqualTo("Jane Updated");
        assertThat(response.phone()).isEqualTo("9999999999");
        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(admin.getFullName()).isEqualTo("Jane Updated");
        verify(communityAdminRepository).save(admin);
    }
}
