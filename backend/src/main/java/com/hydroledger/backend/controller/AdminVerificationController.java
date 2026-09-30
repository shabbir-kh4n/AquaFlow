package com.hydroledger.backend.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hydroledger.backend.dto.VerificationResponse;
import com.hydroledger.backend.dto.VerificationSubmission;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.AdminVerificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/verification")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminVerificationController {

    private final AdminVerificationService verificationService;

    @GetMapping
    public ResponseEntity<VerificationResponse> get(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(verificationService.getMine(principal.getUser()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationResponse> submit(
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String documentNumber,
            @RequestParam(required = false) String details,
            @RequestParam(required = false) String communityName,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String pincode,
            @RequestParam(required = false) Integer totalFlats,
            @RequestParam(required = false) Integer totalBlocks,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        VerificationSubmission submission = new VerificationSubmission(documentType, documentNumber, details,
                communityName, address, city, state, pincode, totalFlats, totalBlocks);
        return ResponseEntity.ok(verificationService.submit(principal.getUser(), submission, file));
    }

    @GetMapping("/document")
    public ResponseEntity<byte[]> document(@AuthenticationPrincipal UserPrincipal principal) {
        return pdf(verificationService.getMyDocument(principal.getUser()));
    }

    static ResponseEntity<byte[]> pdf(AdminVerificationService.Document doc) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(doc.fileName()).build().toString())
                .body(doc.data());
    }
}
