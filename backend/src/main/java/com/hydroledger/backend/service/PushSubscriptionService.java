package com.hydroledger.backend.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hydroledger.backend.dto.PushSubscribeRequest;
import com.hydroledger.backend.entity.PushSubscription;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.repository.PushSubscriptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository pushSubscriptionRepository;

    @Transactional
    public void subscribe(Resident resident, PushSubscribeRequest request) {
        Optional<PushSubscription> existing = pushSubscriptionRepository.findByEndpoint(request.endpoint());

        PushSubscription subscription = existing.orElseGet(PushSubscription::new);
        subscription.setResident(resident);
        subscription.setEndpoint(request.endpoint());
        subscription.setP256dhKey(request.keys().p256dh());
        subscription.setAuthKey(request.keys().auth());

        pushSubscriptionRepository.save(subscription);
    }

    @Transactional
    public void unsubscribe(Resident resident, String endpoint) {
        pushSubscriptionRepository.deleteByResidentAndEndpoint(resident, endpoint);
    }
}
