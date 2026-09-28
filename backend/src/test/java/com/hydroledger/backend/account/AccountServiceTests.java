package com.hydroledger.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;

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
import com.hydroledger.backend.service.AccountService;

@ExtendWith(MockitoExtension.class)
class AccountServiceTests {

    @Mock
    private CommunityAdminRepository communityAdminRepository;

    @Mock
    private ResidentRepository residentRepository;

    @Mock
    private SystemAdminRepository systemAdminRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService accountService;

    private final ChangePasswordRequest request = new ChangePasswordRequest("oldPassword1", "newPassword1");

    @Test
    void residentCanChangePassword() {
        Resident resident = Resident.builder().id(2L).email("res@example.com").password("old-hash").build();
        when(residentRepository.findById(2L)).thenReturn(Optional.of(resident));
        when(passwordEncoder.matches("oldPassword1", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newPassword1")).thenReturn("new-hash");

        accountService.changePassword(new ResidentPrincipal(resident), request);

        assertThat(resident.getPassword()).isEqualTo("new-hash");
        verify(residentRepository).save(resident);
    }

    @Test
    void communityAdminCanChangePassword() {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).email("jane@example.com").password("old-hash").build();
        when(communityAdminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("oldPassword1", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newPassword1")).thenReturn("new-hash");

        accountService.changePassword(new UserPrincipal(admin), request);

        assertThat(admin.getPassword()).isEqualTo("new-hash");
        verify(communityAdminRepository).save(admin);
    }

    @Test
    void systemAdminCanChangePassword() {
        SystemAdmin systemAdmin = SystemAdmin.builder().id(9L).email("root@hydroledger.local").password("old-hash").build();
        when(systemAdminRepository.findById(9L)).thenReturn(Optional.of(systemAdmin));
        when(passwordEncoder.matches("oldPassword1", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newPassword1")).thenReturn("new-hash");

        accountService.changePassword(new SystemAdminPrincipal(systemAdmin), request);

        assertThat(systemAdmin.getPassword()).isEqualTo("new-hash");
        verify(systemAdminRepository).save(systemAdmin);
    }

    @Test
    void wrongCurrentPasswordIsBadRequestAndNothingIsSaved() {
        CommunityAdmin admin = CommunityAdmin.builder().id(1L).email("jane@example.com").password("old-hash").build();
        when(communityAdminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("oldPassword1", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> accountService.changePassword(new UserPrincipal(admin), request))
                .isInstanceOf(ApiException.class)
                .hasMessage("Current password is incorrect")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(admin.getPassword()).isEqualTo("old-hash");
        verify(communityAdminRepository, never()).save(any());
    }

    @Test
    void newPasswordSameAsCurrentIsBadRequest() {
        Resident resident = Resident.builder().id(2L).email("res@example.com").password("old-hash").build();
        when(residentRepository.findById(2L)).thenReturn(Optional.of(resident));
        when(passwordEncoder.matches("samePassword1", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> accountService.changePassword(new ResidentPrincipal(resident),
                new ChangePasswordRequest("samePassword1", "samePassword1")))
                .isInstanceOf(ApiException.class)
                .hasMessage("New password must be different from your current password")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(residentRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void accountWithoutStoredPasswordIsRejectedAsWrongCurrent() {
        Resident resident = Resident.builder().id(2L).email("res@example.com").password(null).build();
        when(residentRepository.findById(2L)).thenReturn(Optional.of(resident));

        assertThatThrownBy(() -> accountService.changePassword(new ResidentPrincipal(resident), request))
                .isInstanceOf(ApiException.class)
                .hasMessage("Current password is incorrect");
    }

    @Test
    void unknownPrincipalTypeIsForbidden() {
        User other = new User("x@example.com", "pw", List.of());

        assertThatThrownBy(() -> accountService.changePassword(other, request))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
