package com.hydroledger.backend.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.hydroledger.backend.exception.ApiException;
import com.hydroledger.backend.service.ChatRateLimiterService;

class ChatRateLimiterServiceTests {

    @Test
    void allowsRequestsWithinCapacity() {
        ChatRateLimiterService service = new ChatRateLimiterService(3, 10);

        service.checkAndConsume("key-1");
        service.checkAndConsume("key-1");
        service.checkAndConsume("key-1");
    }

    @Test
    void rejectsRequestsBeyondCapacity() {
        ChatRateLimiterService service = new ChatRateLimiterService(2, 10);

        service.checkAndConsume("key-2");
        service.checkAndConsume("key-2");

        assertThatThrownBy(() -> service.checkAndConsume("key-2"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void tracksEachKeyIndependently() {
        ChatRateLimiterService service = new ChatRateLimiterService(1, 10);

        service.checkAndConsume("key-a");
        service.checkAndConsume("key-b");
    }
}
