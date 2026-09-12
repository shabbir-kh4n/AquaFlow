package com.shabbir.backend.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.shabbir.backend.exception.ApiException;

/**
 * Hand-rolled in-memory token bucket, keyed per authenticated principal id (or
 * client IP for the public persona). Protects the shared free-tier Gemini
 * quota from being exhausted by any single caller. Not distributed/persisted
 * on purpose - a single backend instance is all this project runs.
 */
@Service
public class ChatRateLimiterService {

    private final ConcurrentHashMap<String, AtomicReference<Bucket>> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final double refillPerMillis;

    public ChatRateLimiterService(
            @Value("${chat.rate-limit.capacity}") int capacity,
            @Value("${chat.rate-limit.refill-per-minute}") int refillPerMinute) {
        this.capacity = capacity;
        this.refillPerMillis = refillPerMinute / 60_000.0;
    }

    public void checkAndConsume(String key) {
        AtomicReference<Bucket> ref = buckets.computeIfAbsent(key,
                k -> new AtomicReference<>(new Bucket(capacity, System.currentTimeMillis())));

        Bucket updated = ref.updateAndGet(bucket -> bucket.refill(System.currentTimeMillis(), capacity, refillPerMillis));
        if (updated.tokens() < 1) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "You're sending messages too quickly - please wait a moment and try again.");
        }
        ref.updateAndGet(Bucket::consumeOne);
    }

    private record Bucket(double tokens, long lastRefillMillis) {

        Bucket refill(long now, int capacity, double refillPerMillis) {
            double refilled = Math.min(capacity, tokens + (now - lastRefillMillis) * refillPerMillis);
            return new Bucket(refilled, now);
        }

        Bucket consumeOne() {
            return new Bucket(Math.max(0, tokens - 1), lastRefillMillis);
        }
    }
}
