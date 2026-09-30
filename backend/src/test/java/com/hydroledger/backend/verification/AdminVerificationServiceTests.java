package com.hydroledger.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.hydroledger.backend.dto.VerificationResponse;
import com.hydroledger.backend.dto.VerificationSubmission;
import com.hydroledger.backend.entity.AdminVerification;
import com.hydroledger.backend.entity.CommunityAdmin;
import com.hydroledger.backend.entity.VerificationStatus;
import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.repository.AdminVerificationRepository;
import com.hydroledger.backend.repository.CommunityAdminRepository;
import com.hydroledger.backend.service.AdminVerificationService;

@ExtendWith(MockitoExtension.class)
class AdminVerificationServiceTests {

    @Mock
    private AdminVerificationRepository verificationRepository;

    @Mock
    private CommunityAdminRepository communityAdminRepository;

    @InjectMocks
    private AdminVerificationService service;

    private final CommunityAdmin admin = CommunityAdmin.builder().id(5L).fullName("A").email("a@x.com").build();

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF-1.4 test".getBytes());
    }

    private AdminVerification existing(VerificationStatus status) {
        return AdminVerification.builder().admin(admin).documentType("GST").documentNumber("1")
                .fileName("old.pdf").fileSize(3).fileData(new byte[] {1}).status(status).build();
    }

    private static VerificationSubmission sub(String type, String number, String details) {
        return new VerificationSubmission(type, number, details, "Sunrise", "1 Main St", "Pune", "MH", "411001", 40, null);
    }

    private static HttpStatus statusOf(Throwable t) {
        return (HttpStatus) ((ApiException) t).getStatus();
    }

    @Test
    void getMineReportsNotSubmittedWhenNothingOnFile() {
        when(verificationRepository.findByAdmin(admin)).thenReturn(Optional.empty());
        assertThat(service.getMine(admin).status()).isEqualTo(VerificationStatus.NOT_SUBMITTED);
    }

    @Test
    void submitCreatesPendingVerification() {
        when(verificationRepository.findByAdmin(admin)).thenReturn(Optional.empty());
        when(verificationRepository.save(any(AdminVerification.class))).thenAnswer(i -> i.getArgument(0));

        VerificationResponse response = service.submit(admin, sub(" GST ", " 29ABC ", "  "), pdf());

        assertThat(response.status()).isEqualTo(VerificationStatus.PENDING);
        assertThat(response.documentType()).isEqualTo("GST");
        assertThat(response.documentNumber()).isEqualTo("29ABC");
        assertThat(response.details()).isNull();
    }

    @Test
    void submitRejectsNonPdfContent() {
        MockMultipartFile fake = new MockMultipartFile("file", "doc.pdf", "application/pdf", "not a pdf".getBytes());
        assertThatThrownBy(() -> service.submit(admin, sub("GST", "1", null), fake))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void submitAfterRejectionResetsToPendingAndClearsReason() {
        AdminVerification rejected = existing(VerificationStatus.REJECTED);
        rejected.setRejectionReason("Blurry");
        when(verificationRepository.findByAdmin(admin)).thenReturn(Optional.of(rejected));
        when(verificationRepository.save(any(AdminVerification.class))).thenAnswer(i -> i.getArgument(0));

        VerificationResponse response = service.submit(admin, sub("GST", "2", null), pdf());

        assertThat(response.status()).isEqualTo(VerificationStatus.PENDING);
        assertThat(response.rejectionReason()).isNull();
    }

    @Test
    void submitIsRejectedOnceApproved() {
        when(verificationRepository.findByAdmin(admin)).thenReturn(Optional.of(existing(VerificationStatus.APPROVED)));
        assertThatThrownBy(() -> service.submit(admin, sub("GST", "1", null), pdf()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectRequiresReason() {
        assertThatThrownBy(() -> service.reject(5L, "  "))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectStoresReasonAndCannotBeDecidedTwice() {
        AdminVerification pending = existing(VerificationStatus.PENDING);
        when(communityAdminRepository.findById(5L)).thenReturn(Optional.of(admin));
        when(verificationRepository.findByAdmin(admin)).thenReturn(Optional.of(pending));

        VerificationResponse rejected = service.reject(5L, " Expired document ");
        assertThat(rejected.status()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(rejected.rejectionReason()).isEqualTo("Expired document");

        assertThatThrownBy(() -> service.approve(5L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void approveMarksApproved() {
        AdminVerification pending = existing(VerificationStatus.PENDING);
        when(communityAdminRepository.findById(5L)).thenReturn(Optional.of(admin));
        when(verificationRepository.findByAdmin(admin)).thenReturn(Optional.of(pending));

        VerificationResponse response = service.approve(5L);

        assertThat(response.status()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(response.reviewedAt()).isNotNull();
    }
}
