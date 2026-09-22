package com.hydroledger.backend.config;

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import nl.martijndwars.webpush.PushService;

@Configuration
public class PushConfig {

    @Bean
    public PushService pushService(
            @Value("${app.push.vapid-public-key}") String vapidPublicKey,
            @Value("${app.push.vapid-private-key}") String vapidPrivateKey,
            @Value("${app.push.vapid-subject}") String vapidSubject) throws Exception {
        if (vapidPublicKey == null || vapidPublicKey.isBlank()
                || vapidPrivateKey == null || vapidPrivateKey.isBlank()) {
            // No VAPID keypair configured (e.g. local dev without one) - push sending stays a no-op,
            // PushNotificationService checks for this via ObjectProvider.
            return null;
        }
        Security.addProvider(new BouncyCastleProvider());
        return new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
    }
}
