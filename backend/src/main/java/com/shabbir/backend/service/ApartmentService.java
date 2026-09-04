package com.shabbir.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shabbir.backend.dto.ApartmentRequest;
import com.shabbir.backend.dto.ApartmentResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.CommunityAdmin;
import com.shabbir.backend.repository.ApartmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApartmentService {

    private final ApartmentRepository apartmentRepository;

    @Transactional
    public Apartment getOrCreate(CommunityAdmin admin) {
        return apartmentRepository.findByAdmin(admin)
                .orElseGet(() -> apartmentRepository.save(
                        Apartment.builder()
                                .name(admin.getFullName() + "'s Apartment")
                                .admin(admin)
                                .build()
                ));
    }

    @Transactional
    public Apartment getOrCreate(CommunityAdmin admin, String name) {
        return apartmentRepository.findByAdmin(admin)
                .orElseGet(() -> apartmentRepository.save(
                        Apartment.builder()
                                .name(name)
                                .admin(admin)
                                .build()
                ));
    }

    public ApartmentResponse getForAdmin(CommunityAdmin admin) {
        return ApartmentResponse.from(getOrCreate(admin));
    }

    @Transactional
    public ApartmentResponse update(ApartmentRequest request, CommunityAdmin admin) {
        Apartment apartment = getOrCreate(admin);
        apartment.setName(request.name());
        apartment.setAddress(request.address());
        apartment.setMainsSource(request.mainsSource());
        return ApartmentResponse.from(apartment);
    }
}
