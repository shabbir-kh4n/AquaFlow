package com.hydroledger.backend.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hydroledger.backend.dto.PushSubscribeRequest;
import com.hydroledger.backend.entity.PushSubscription;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.repository.PushSubscriptionRepository;
import com.hydroledger.backend.service.PushSubscriptionService;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTests {

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    @InjectMocks
    private PushSubscriptionService pushSubscriptionService;

    private Resident resident;

    @BeforeEach
    void setUp() {
        resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").build();
    }

    @Test
    void subscribeCreatesNewSubscriptionWhenEndpointUnknown() {
        PushSubscribeRequest request = new PushSubscribeRequest("https://push.example.com/abc",
                new PushSubscribeRequest.Keys("p256dh-value", "auth-value"));
        when(pushSubscriptionRepository.findByEndpoint("https://push.example.com/abc")).thenReturn(Optional.empty());

        pushSubscriptionService.subscribe(resident, request);

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(pushSubscriptionRepository).save(captor.capture());
        PushSubscription saved = captor.getValue();
        assertThat(saved.getResident()).isEqualTo(resident);
        assertThat(saved.getEndpoint()).isEqualTo("https://push.example.com/abc");
        assertThat(saved.getP256dhKey()).isEqualTo("p256dh-value");
        assertThat(saved.getAuthKey()).isEqualTo("auth-value");
    }

    @Test
    void subscribeReassignsExistingSubscriptionToNewResident() {
        Resident otherResident = Resident.builder().id(2L).fullName("Other").email("other@example.com").build();
        PushSubscription existing = PushSubscription.builder().id(9L).resident(otherResident)
                .endpoint("https://push.example.com/abc").p256dhKey("old-key").authKey("old-auth").build();
        PushSubscribeRequest request = new PushSubscribeRequest("https://push.example.com/abc",
                new PushSubscribeRequest.Keys("new-key", "new-auth"));
        when(pushSubscriptionRepository.findByEndpoint("https://push.example.com/abc")).thenReturn(Optional.of(existing));

        pushSubscriptionService.subscribe(resident, request);

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(pushSubscriptionRepository).save(captor.capture());
        PushSubscription saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(9L);
        assertThat(saved.getResident()).isEqualTo(resident);
        assertThat(saved.getP256dhKey()).isEqualTo("new-key");
        assertThat(saved.getAuthKey()).isEqualTo("new-auth");
    }

    @Test
    void unsubscribeDeletesByResidentAndEndpoint() {
        pushSubscriptionService.unsubscribe(resident, "https://push.example.com/abc");

        verify(pushSubscriptionRepository).deleteByResidentAndEndpoint(resident, "https://push.example.com/abc");
    }
}
