package com.hydroledger.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.Apartment;
import com.hydroledger.backend.entity.BulkWaterPurchase;

public interface BulkWaterPurchaseRepository extends JpaRepository<BulkWaterPurchase, Long> {

    List<BulkWaterPurchase> findByApartmentOrderByPurchaseDateDesc(Apartment apartment);

    Optional<BulkWaterPurchase> findByIdAndApartment(Long id, Apartment apartment);
}
