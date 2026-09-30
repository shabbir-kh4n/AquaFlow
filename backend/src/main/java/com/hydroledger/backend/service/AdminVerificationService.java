package com.hydroledger.backend.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hydroledger.backend.dto.VerificationResponse;
import com.hydroledger.backend.dto.VerificationSubmission;
import com.hydroledger.backend.entity.AdminVerification;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.VerificationStatus;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.AdminVerificationRepository;
import com.hydroledger.backend.repository.CommunityAdminRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminVerificationService {

    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private final AdminVerificationRepository verificationRepository;
    private final CommunityAdminRepository communityAdminRepository;

    /** A downloadable copy of the submitted PDF. */
    public record Document(String fileName, byte[] data) {
    }

    // ---- Community admin side ----

    @Transactional(readOnly = true)
    public VerificationResponse getMine(CommunityAdmin admin) {
        return toResponse(verificationRepository.findByAdmin(admin).orElse(null));
    }

    @Transactional
    public VerificationResponse submit(CommunityAdmin admin, VerificationSubmission in, MultipartFile file) {
        String documentType = requireText(in.documentType(), 60, "Document type");
        String documentNumber = requireText(in.documentNumber(), 100, "Document number");
        String communityName = requireText(in.communityName(), 200, "Community name");
        String address = requireText(in.address(), 500, "Address");
        String city = requireText(in.city(), 100, "City");
        String state = requireText(in.state(), 100, "State");
        String pincode = requireText(in.pincode(), 12, "Pincode");
        if (in.totalFlats() == null || in.totalFlats() < 1 || in.totalFlats() > 100000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Total flats must be a number of at least 1");
        }
        if (in.totalBlocks() != null && (in.totalBlocks() < 1 || in.totalBlocks() > 10000)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Total blocks must be a number of at least 1");
        }
        String details = in.details();
        if (details != null && details.length() > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Details must be at most 2000 characters");
        }

        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A PDF document is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Document must be 5 MB or smaller");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read the uploaded document");
        }
        if (!isPdf(data)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Document must be a PDF file");
        }

        AdminVerification verification = verificationRepository.findByAdmin(admin).orElse(null);
        if (verification != null && verification.getStatus() == VerificationStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Your documents are already verified");
        }
        if (verification == null) {
            verification = new AdminVerification();
            verification.setAdmin(admin);
        }

        String originalName = file.getOriginalFilename();
        verification.setDocumentType(documentType);
        verification.setDocumentNumber(documentNumber);
        verification.setCommunityName(communityName);
        verification.setAddress(address);
        verification.setCity(city);
        verification.setState(state);
        verification.setPincode(pincode);
        verification.setTotalFlats(in.totalFlats());
        verification.setTotalBlocks(in.totalBlocks());
        verification.setDetails(details == null || details.isBlank() ? null : details.trim());
        verification.setFileName(originalName == null || originalName.isBlank() ? "document.pdf" : originalName);
        verification.setFileSize(data.length);
        verification.setFileData(data);
        verification.setStatus(VerificationStatus.PENDING);
        verification.setRejectionReason(null);
        verification.setReviewedAt(null);
        verification.setSubmittedAt(Instant.now());
        return toResponse(verificationRepository.save(verification));
    }

    @Transactional(readOnly = true)
    public Document getMyDocument(CommunityAdmin admin) {
        return toDocument(verificationRepository.findByAdmin(admin).orElse(null));
    }

    @Transactional(readOnly = true)
    public VerificationStatus statusFor(CommunityAdmin admin) {
        return verificationRepository.findByAdmin(admin)
                .map(AdminVerification::getStatus)
                .orElse(VerificationStatus.NOT_SUBMITTED);
    }

    // ---- System admin side ----

    @Transactional(readOnly = true)
    public VerificationResponse getForAdmin(Long adminId) {
        return toResponse(verificationRepository.findByAdmin(requireAdmin(adminId)).orElse(null));
    }

    @Transactional(readOnly = true)
    public Document getDocumentForAdmin(Long adminId) {
        return toDocument(verificationRepository.findByAdmin(requireAdmin(adminId)).orElse(null));
    }

    @Transactional
    public VerificationResponse approve(Long adminId) {
        AdminVerification verification = requirePending(adminId);
        verification.setStatus(VerificationStatus.APPROVED);
        verification.setRejectionReason(null);
        verification.setReviewedAt(Instant.now());
        return toResponse(verification);
    }

    @Transactional
    public VerificationResponse reject(Long adminId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A reason for rejection is required");
        }
        AdminVerification verification = requirePending(adminId);
        verification.setStatus(VerificationStatus.REJECTED);
        verification.setRejectionReason(reason.trim());
        verification.setReviewedAt(Instant.now());
        return toResponse(verification);
    }

    private AdminVerification requirePending(Long adminId) {
        AdminVerification verification = verificationRepository.findByAdmin(requireAdmin(adminId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No verification has been submitted"));
        if (verification.getStatus() != VerificationStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "This verification has already been reviewed");
        }
        return verification;
    }

    private CommunityAdmin requireAdmin(Long adminId) {
        return communityAdminRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Community admin not found"));
    }

    private static Document toDocument(AdminVerification verification) {
        if (verification == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No verification has been submitted");
        }
        return new Document(verification.getFileName(), verification.getFileData());
    }

    private static String requireText(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + " must be at most " + max + " characters");
        }
        return trimmed;
    }

    private static boolean isPdf(byte[] data) {
        if (data.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (data[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static VerificationResponse toResponse(AdminVerification v) {
        if (v == null) {
            return VerificationResponse.notSubmitted();
        }
        return new VerificationResponse(v.getStatus(), v.getDocumentType(), v.getDocumentNumber(), v.getDetails(),
                v.getCommunityName(), v.getAddress(), v.getCity(), v.getState(), v.getPincode(),
                v.getTotalFlats(), v.getTotalBlocks(), v.getFileName(), v.getFileSize(), v.getRejectionReason(), v.getSubmittedAt(), v.getReviewedAt());
    }
}
