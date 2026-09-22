package com.hydroledger.backend.service;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.hydroledger.backend.entity.PushSubscription;
import com.hydroledger.backend.entity.Resident;
import com.hydroledger.backend.repository.PushSubscriptionRepository;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushService pushService;

    public PushNotificationService(PushSubscriptionRepository pushSubscriptionRepository,
            ObjectProvider<PushService> pushServiceProvider) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.pushService = pushServiceProvider.getIfAvailable();
    }

    public void sendInvoiceReadyPush(Resident resident, String periodMonth, BigDecimal totalAmount, String link) {
        sendToResident(resident, "Your water bill is ready",
                "Your bill for " + periodMonth + " is ₹" + totalAmount + ".", link);
    }

    public void sendNewAlertPush(Resident resident, String alertTypeLabel, String message, String link) {
        sendToResident(resident, alertTypeLabel, message, link);
    }

    private void sendToResident(Resident resident, String title, String body, String link) {
        if (pushService == null) {
            // No VAPID keys configured - push sending is a no-op, mirrors EmailService's
            // silent-skip behavior when mail credentials aren't set.
            return;
        }

        List<PushSubscription> subscriptions = pushSubscriptionRepository.findByResident(resident);
        if (subscriptions.isEmpty()) {
            return;
        }

        String payload = toJsonPayload(title, body, link);
        for (PushSubscription subscription : subscriptions) {
            try {
                Notification notification = new Notification(
                        subscription.getEndpoint(),
                        subscription.getP256dhKey(),
                        subscription.getAuthKey(),
                        payload);
                pushService.send(notification);
            } catch (Exception ex) {
                // Never let a push-send failure propagate - this is called from inside
                // @Transactional alert-save / invoice-finalize flows and must not roll them back.
                log.warn("Failed to send push notification to resident {}: {}", resident.getId(), ex.getMessage());
            }
        }
    }

    private String toJsonPayload(String title, String body, String link) {
        return "{\"title\":\"" + jsonEscape(title) + "\","
                + "\"body\":\"" + jsonEscape(body) + "\","
                + "\"link\":\"" + jsonEscape(link) + "\"}";
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
