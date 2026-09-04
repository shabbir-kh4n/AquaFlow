package com.shabbir.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shabbir.backend.dto.WaterMeterRequest;
import com.shabbir.backend.dto.WaterMeterResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.security.UserPrincipal;
import com.shabbir.backend.service.ApartmentService;
import com.shabbir.backend.service.WaterMeterService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/meters")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class WaterMeterController {

    private final WaterMeterService waterMeterService;
    private final ApartmentService apartmentService;

    @GetMapping
    public ResponseEntity<List<WaterMeterResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(waterMeterService.list(apartmentService.getOrCreate(principal.getUser())));
    }

    @GetMapping("/{meterNumber}")
    public ResponseEntity<WaterMeterResponse> get(@PathVariable String meterNumber,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(waterMeterService.get(meterNumber, apartmentService.getOrCreate(principal.getUser())));
    }

    @PostMapping
    public ResponseEntity<WaterMeterResponse> create(@Valid @RequestBody WaterMeterRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(waterMeterService.create(request, apartment));
    }

    @PutMapping("/{meterNumber}")
    public ResponseEntity<WaterMeterResponse> update(@PathVariable String meterNumber,
            @Valid @RequestBody WaterMeterRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        Apartment apartment = apartmentService.getOrCreate(principal.getUser());
        return ResponseEntity.ok(waterMeterService.update(meterNumber, request, apartment));
    }
}
