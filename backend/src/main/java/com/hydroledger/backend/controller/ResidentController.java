package com.hydroledger.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hydroledger.backend.dto.CsvUploadResultResponse;
import com.hydroledger.backend.dto.ResidentRequest;
import com.hydroledger.backend.dto.ResidentResponse;
import com.hydroledger.backend.security.UserPrincipal;
import com.hydroledger.backend.service.ResidentBulkUploadService;
import com.hydroledger.backend.service.ResidentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/residents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ResidentController {

    private final ResidentService residentService;
    private final ResidentBulkUploadService residentBulkUploadService;

    @PostMapping
    public ResponseEntity<ResidentResponse> create(@Valid @RequestBody ResidentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(residentService.create(request, principal.getUser()));
    }

    @GetMapping
    public ResponseEntity<List<ResidentResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(residentService.listForAdmin(principal.getUser()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResidentResponse> update(@PathVariable Long id, @Valid @RequestBody ResidentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(residentService.update(id, request, principal.getUser()));
    }

    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvUploadResultResponse> bulkUpload(@RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(residentBulkUploadService.bulkUpload(file, principal.getUser()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        residentService.delete(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }
}
