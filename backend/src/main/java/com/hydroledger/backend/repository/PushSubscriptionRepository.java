package com.hydroledger.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hydroledger.backend.entity.PushSubscription;
import com.hydroledger.backend.entity.Resident;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByResident(Resident resident);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    void deleteByResidentAndEndpoint(Resident resident, String endpoint);

    void deleteByResident(Resident resident);
}
