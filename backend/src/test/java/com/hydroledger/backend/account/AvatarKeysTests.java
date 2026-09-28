package com.hydroledger.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.hydroledger.backend.entity.Role;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.service.AvatarKeys;

class AvatarKeysTests {

    @Test
    void acceptsEachRolesOwnKeys() {
        assertThat(AvatarKeys.validate(Role.RESIDENT, "resident-01")).isEqualTo("resident-01");
        assertThat(AvatarKeys.validate(Role.ADMIN, "admin-07")).isEqualTo("admin-07");
        assertThat(AvatarKeys.validate(Role.SYSTEM_ADMIN, "system-12")).isEqualTo("system-12");
    }

    @Test
    void nullOrBlankClearsTheAvatar() {
        assertThat(AvatarKeys.validate(Role.RESIDENT, null)).isNull();
        assertThat(AvatarKeys.validate(Role.ADMIN, "")).isNull();
        assertThat(AvatarKeys.validate(Role.SYSTEM_ADMIN, "   ")).isNull();
    }

    @Test
    void rejectsKeyFromAnotherRolesSet() {
        assertBadRequest(Role.RESIDENT, "admin-03");
        assertBadRequest(Role.ADMIN, "system-03");
        assertBadRequest(Role.SYSTEM_ADMIN, "resident-03");
    }

    @Test
    void rejectsMalformedOrOutOfRangeKeys() {
        List<String> bad = List.of("resident-00", "resident-13", "resident-7", "resident-007", "Resident-01",
                "resident_01", "resident-ab", "../resident-01", "resident-01.svg", "tenant-01");
        for (String key : bad) {
            assertBadRequest(Role.RESIDENT, key);
        }
    }

    private void assertBadRequest(Role role, String key) {
        assertThatThrownBy(() -> AvatarKeys.validate(role, key))
                .as("key %s for %s", key, role)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
