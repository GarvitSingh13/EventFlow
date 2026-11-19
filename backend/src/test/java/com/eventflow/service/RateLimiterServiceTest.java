package com.eventflow.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
    }

    @Test
    @DisplayName("Should permit requests within rate limit capacity")
    void testPermitWithinCapacity() {
        UUID endpointId = UUID.randomUUID();
        int limit = 5;

        for (int i = 0; i < limit; i++) {
            assertTrue(rateLimiterService.tryAcquire(endpointId, limit), "Should allow request " + (i + 1));
        }
    }

    @Test
    @DisplayName("Should throttle requests when burst limit is exhausted")
    void testThrottleWhenExhausted() {
        UUID endpointId = UUID.randomUUID();
        int limit = 3;

        // Exhaust tokens
        for (int i = 0; i < limit; i++) {
            assertTrue(rateLimiterService.tryAcquire(endpointId, limit));
        }

        // Next immediate request should be throttled
        assertFalse(rateLimiterService.tryAcquire(endpointId, limit), "Should throttle 4th request when limit is 3");
    }

    @Test
    @DisplayName("Endpoints should maintain independent token buckets")
    void testIsolatedEndpoints() {
        UUID ep1 = UUID.randomUUID();
        UUID ep2 = UUID.randomUUID();

        // Exhaust ep1
        assertTrue(rateLimiterService.tryAcquire(ep1, 1));
        assertFalse(rateLimiterService.tryAcquire(ep1, 1));

        // ep2 should still have available capacity
        assertTrue(rateLimiterService.tryAcquire(ep2, 1), "Endpoint 2 must not be throttled by Endpoint 1");
    }

    @Test
    @DisplayName("Rate limit <= 0 should be treated as unlimited")
    void testUnlimitedWhenZeroOrNegative() {
        UUID endpointId = UUID.randomUUID();
        for (int i = 0; i < 20; i++) {
            assertTrue(rateLimiterService.tryAcquire(endpointId, 0));
            assertTrue(rateLimiterService.tryAcquire(endpointId, -1));
        }
    }
}
