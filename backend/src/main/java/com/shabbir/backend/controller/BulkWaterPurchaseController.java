package com.shabbir.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import com.shabbir.backend.dto.BulkWaterPurchaseRequest;
import com.shabbir.backend.dto.BulkWaterPurchaseResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.security.UserPrincipal;
import com.shabbir.backend.service.ApartmentService;
import com.shabbir.backend.service.BulkWaterPurchaseService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/bulk-water-purchases")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BulkWaterPurchaseController {

    private final BulkWaterPurchaseService bulkWaterPurchaseService;
    private final ApartmentService apartmentService;

    @PostMapping
    public ResponseEntity<BulkWaterPurchaseResponse> create(@Valid @RequestBody BulkWaterPurchaseRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(bulkWaterPurchaseService.create(apartment, request));
    }

    @GetMapping
    public ResponseEntity<List<BulkWaterPurchaseResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(bulkWaterPurchaseService.list(apartment));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BulkWaterPurchaseResponse> update(@PathVariable Long id,
            @Valid @RequestBody BulkWaterPurchaseRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(bulkWaterPurchaseService.update(apartment, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        bulkWaterPurchaseService.delete(apartment, id);
        return ResponseEntity.noContent().build();
    }
}
