package com.hydroledger.backend.push;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import com.hydroledger.backend.entity.PushSubscription;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.repository.PushSubscriptionRepository;
import com.hydroledger.backend.service.PushNotificationService;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTests {

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Mock
    private ObjectProvider<PushService> pushServiceProvider;

    @Mock
    private PushService pushService;

    private Resident resident;

    // nl.martijndwars.webpush's Notification constructor decodes these as real EC key material
    // (an uncompressed P-256 point + a 16-byte auth secret), so arbitrary strings fail to parse
    // before ever reaching pushService.send() - generate an actual on-curve key for the tests.
    private String validP256dh;
    private String validAuth;

    @BeforeAll
    static void registerBouncyCastle() {
        // nl.martijndwars.webpush looks up the "BC" provider by name (PushConfig registers this
        // for real at app startup) - tests that construct a Notification directly need it too.
        Security.addProvider(new BouncyCastleProvider());
    }

    @BeforeEach
    void setUp() throws Exception {
        resident = Resident.builder().id(1L).fullName("Res").email("res@example.com").build();
        validP256dh = base64Url(generateUncompressedP256Point());
        validAuth = base64Url(new byte[16]);
    }

    @Test
    void sendInvoiceReadyPushSendsOnePerSubscriptionWhenConfigured() throws Exception {
        when(pushServiceProvider.getIfAvailable()).thenReturn(pushService);
        PushNotificationService service = new PushNotificationService(pushSubscriptionRepository, pushServiceProvider);

        PushSubscription sub1 = PushSubscription.builder().id(1L).resident(resident)
                .endpoint("https://push.example.com/a").p256dhKey(validP256dh).authKey(validAuth).build();
        PushSubscription sub2 = PushSubscription.builder().id(2L).resident(resident)
                .endpoint("https://push.example.com/b").p256dhKey(validP256dh).authKey(validAuth).build();
        when(pushSubscriptionRepository.findByResident(resident)).thenReturn(List.of(sub1, sub2));

        service.sendInvoiceReadyPush(resident, "2026-09", new BigDecimal("190.00"), "https://app/resident/billing");

        verify(pushService, times(2)).send(any(Notification.class));
    }

    @Test
    void sendNewAlertPushIsNoOpWhenVapidNotConfigured() {
        when(pushServiceProvider.getIfAvailable()).thenReturn(null);
        PushNotificationService service = new PushNotificationService(pushSubscriptionRepository, pushServiceProvider);

        service.sendNewAlertPush(resident, "Threshold exceeded", "message", "https://app/resident/notifications");

        verify(pushSubscriptionRepository, never()).findByResident(any());
    }

    @Test
    void sendSwallowsExceptionFromPushServiceSend() throws Exception {
        when(pushServiceProvider.getIfAvailable()).thenReturn(pushService);
        PushNotificationService service = new PushNotificationService(pushSubscriptionRepository, pushServiceProvider);

        PushSubscription sub = PushSubscription.builder().id(1L).resident(resident)
                .endpoint("https://push.example.com/a").p256dhKey(validP256dh).authKey(validAuth).build();
        when(pushSubscriptionRepository.findByResident(resident)).thenReturn(List.of(sub));
        doThrow(new RuntimeException("boom")).when(pushService).send(any(Notification.class));

        // Must not throw - a failed push send can never propagate out of the notification service.
        service.sendNewAlertPush(resident, "Threshold exceeded", "message", "https://app/resident/notifications");

        verify(pushService).send(any(Notification.class));
    }

    private static byte[] generateUncompressedP256Point() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        ECPublicKey publicKey = (ECPublicKey) generator.generateKeyPair().getPublic();

        byte[] x = toFixedLength(publicKey.getW().getAffineX().toByteArray(), 32);
        byte[] y = toFixedLength(publicKey.getW().getAffineY().toByteArray(), 32);

        byte[] uncompressed = new byte[65];
        uncompressed[0] = 0x04;
        System.arraycopy(x, 0, uncompressed, 1, 32);
        System.arraycopy(y, 0, uncompressed, 33, 32);
        return uncompressed;
    }

    private static byte[] toFixedLength(byte[] input, int length) {
        if (input.length == length) {
            return input;
        }
        byte[] output = new byte[length];
        if (input.length > length) {
            System.arraycopy(input, input.length - length, output, 0, length);
        } else {
            System.arraycopy(input, 0, output, length - input.length, input.length);
        }
        return output;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
