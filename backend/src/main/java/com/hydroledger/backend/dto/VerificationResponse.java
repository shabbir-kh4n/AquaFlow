package com.hydroledger.backend.dto;

import java.time.Instant;

import com.hydroledger.backend.entity.VerificationStatus;

// Never carries the PDF bytes - those are served by the separate /document endpoints.
public record VerificationResponse(
        VerificationStatus status,
        String documentType,
        String documentNumber,
        String details,
        String communityName,
        String address,
        String city,
        String state,
        String pincode,
        Integer totalFlats,
        Integer totalBlocks,
        String fileName,
        Long fileSize,
        String rejectionReason,
        Instant submittedAt,
        Instant reviewedAt
) {
    public static VerificationResponse notSubmitted() {
        return new VerificationResponse(VerificationStatus.NOT_SUBMITTED, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }
}
