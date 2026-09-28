package com.hydroledger.backend.dto;

import jakarta.validation.constraints.Size;

// avatarKey may be null (or blank) to clear the avatar and fall back to initials.
public record AvatarUpdateRequest(
        @Size(max = 32, message = "Avatar key is too long")
        String avatarKey
) {
}
