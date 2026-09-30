package com.hydroledger.backend.entity;

public enum VerificationStatus {
    // Never persisted - reported when a community admin has not submitted a document yet.
    NOT_SUBMITTED,
    PENDING,
    APPROVED,
    REJECTED
}
