package com.hydroledger.backend.dto;

// The text fields of a verification submission (the PDF travels separately as a multipart file).
public record VerificationSubmission(
        String documentType,
        String documentNumber,
        String details,
        String communityName,
        String address,
        String city,
        String state,
        String pincode,
        Integer totalFlats,
        Integer totalBlocks
) {
}
