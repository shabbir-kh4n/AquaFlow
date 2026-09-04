package com.shabbir.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shabbir.backend.entity.Apartment;
import com.shabbir.backend.entity.BillingCycle;
import com.shabbir.backend.entity.Household;
import com.shabbir.backend.entity.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByBillingCycle(BillingCycle billingCycle);

    List<Invoice> findByHouseholdOrderByGeneratedAtDesc(Household household);

    Optional<Invoice> findByIdAndBillingCycle_Apartment(Long id, Apartment apartment);
}
