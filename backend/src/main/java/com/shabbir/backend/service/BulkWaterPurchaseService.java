package com.shabbir.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shabbir.backend.dto.BulkWaterPurchaseRequest;
import com.shabbir.backend.dto.BulkWaterPurchaseResponse;
import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.BulkWaterPurchase;
import com.shabbir.backend.exception.ApiException;
import com.shabbir.backend.repository.BulkWaterPurchaseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BulkWaterPurchaseService {

    private final BulkWaterPurchaseRepository bulkWaterPurchaseRepository;

    @Transactional
    public BulkWaterPurchaseResponse create(Apartment apartment, BulkWaterPurchaseRequest request) {
        BulkWaterPurchase purchase = BulkWaterPurchase.builder()
                .apartment(apartment)
                .purchaseDate(request.purchaseDate())
                .source(request.source())
                .volumeLiters(request.volumeLiters())
                .unitCost(request.unitCost())
                .totalCost(computeTotalCost(request.volumeLiters(), request.unitCost()))
                .notes(request.notes())
                .build();

        return BulkWaterPurchaseResponse.from(bulkWaterPurchaseRepository.save(purchase));
    }

    public List<BulkWaterPurchaseResponse> list(Apartment apartment) {
        return bulkWaterPurchaseRepository.findByApartmentOrderByPurchaseDateDesc(apartment).stream()
                .map(BulkWaterPurchaseResponse::from)
                .toList();
    }

    @Transactional
    public BulkWaterPurchaseResponse update(Apartment apartment, Long id, BulkWaterPurchaseRequest request) {
        BulkWaterPurchase purchase = requireOwned(apartment, id);

        purchase.setPurchaseDate(request.purchaseDate());
        purchase.setSource(request.source());
        purchase.setVolumeLiters(request.volumeLiters());
        purchase.setUnitCost(request.unitCost());
        purchase.setTotalCost(computeTotalCost(request.volumeLiters(), request.unitCost()));
        purchase.setNotes(request.notes());

        return BulkWaterPurchaseResponse.from(bulkWaterPurchaseRepository.save(purchase));
    }

    @Transactional
    public void delete(Apartment apartment, Long id) {
        bulkWaterPurchaseRepository.delete(requireOwned(apartment, id));
    }

    private BigDecimal computeTotalCost(BigDecimal volumeLiters, BigDecimal unitCost) {
        return volumeLiters.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
    }

    private BulkWaterPurchase requireOwned(Apartment apartment, Long id) {
        return bulkWaterPurchaseRepository.findByIdAndApartment(id, apartment)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bulk water purchase not found"));
    }
}
