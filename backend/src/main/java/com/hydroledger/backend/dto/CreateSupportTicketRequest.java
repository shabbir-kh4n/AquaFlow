package com.hydroledger.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
        @NotBlank(message = "Subject is required")
        @Size(max = 200, message = "Subject must be at most 200 characters")
        String subject,

        @NotBlank(message = "Message is required")
        @Size(max = 2000, message = "Message must be at most 2000 characters")
        String message,

        // Recipient choice only applies to a resident-raised ticket (an admin's own ticket always
        // goes to System Admin only, unaffected by these fields) - at least one must be true.
        // Nullable (not primitive boolean) so admin-create requests can omit them entirely - this
        // Jackson version throws MismatchedInputException for a missing primitive field, unlike Jackson 2.
        Boolean toAdmin,
        Boolean toSystemAdmin
) {
}
