package com.eventflow.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryWorkerServiceTest {

    private DeliveryWorkerService workerService;

    @BeforeEach
    void setUp() {
        // Pure algorithmic test for jitter backoff calculation (no mocks required)
        workerService = new DeliveryWorkerService(null, null, null);
        ReflectionTestUtils.setField(workerService, "baseDelaySeconds", 2L);
        ReflectionTestUtils.setField(workerService, "maxDelaySeconds", 3600L);
    }

    @RepeatedTest(20)
    @DisplayName("Full Jitter delay on attempt 1 must stay strictly between 1 and 4 seconds")
    void testFullJitterAttempt1Bounds() {
        long delay = workerService.calculateFullJitterDelay(1);
        // Base delay = 2, attempt 1 -> 2 * 2^1 = 4 seconds ceiling
        assertTrue(delay >= 1 && delay <= 4, "Delay must be between 1 and 4 seconds, was: " + delay);
    }

    @RepeatedTest(20)
    @DisplayName("Full Jitter delay on attempt 2 must stay strictly between 1 and 8 seconds")
    void testFullJitterAttempt2Bounds() {
        long delay = workerService.calculateFullJitterDelay(2);
        // Base delay = 2, attempt 2 -> 2 * 2^2 = 8 seconds ceiling
        assertTrue(delay >= 1 && delay <= 8, "Delay must be between 1 and 8 seconds, was: " + delay);
    }

    @Test
    @DisplayName("Full Jitter delay must never exceed maxDelaySeconds ceiling")
    void testFullJitterMaxCeiling() {
        for (int i = 0; i < 50; i++) {
            long delay = workerService.calculateFullJitterDelay(10);
            assertTrue(delay <= 3600, "Delay must never exceed 3600s, was: " + delay);
            assertTrue(delay >= 1, "Delay must be at least 1s, was: " + delay);
        }
    }
}
