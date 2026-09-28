package com.hydroledger.backend.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;

import com.hydroledger.backend.entity.Role;
import com.hydroledger.backend.exception.ApiException;

/**
 * Validates preset avatar keys. Each role picks from its own set of 12 static SVGs
 * (frontend/public/avatars/{resident,admin,system}/01.svg ... 12.svg); a key looks like
 * {@code resident-07}, {@code admin-03} or {@code system-12}.
 */
public final class AvatarKeys {

    public static final int SET_SIZE = 12;

    private static final Pattern FORMAT = Pattern.compile("^(resident|admin|system)-([0-9]{2})$");

    private AvatarKeys() {
    }

    public static String prefixFor(Role role) {
        return switch (role) {
            case RESIDENT -> "resident";
            case ADMIN -> "admin";
            case SYSTEM_ADMIN -> "system";
        };
    }

    /**
     * Returns the validated key, or {@code null} when the input is null/blank (clears the
     * avatar). Throws a 400 ApiException for a malformed key or one from another role's set.
     */
    public static String validate(Role role, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String prefix = prefixFor(role);
        Matcher matcher = FORMAT.matcher(key.trim());
        if (!matcher.matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Invalid avatar. Choose one of " + prefix + "-01 to " + prefix + "-12.");
        }
        int number = Integer.parseInt(matcher.group(2));
        if (!matcher.group(1).equals(prefix) || number < 1 || number > SET_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "That avatar isn't available for your account. Choose one of " + prefix + "-01 to "
                            + prefix + "-12.");
        }
        return key.trim();
    }
}
