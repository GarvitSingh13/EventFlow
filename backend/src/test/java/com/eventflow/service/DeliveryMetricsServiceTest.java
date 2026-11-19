package com.eventflow.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryMetricsServiceTest {

    private MeterRegistry registry;
    private DeliveryMetricsService metricsService;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metricsService = new DeliveryMetricsService(registry);
    }

    @Test
    @DisplayName("Should increment delivery counters according to execution outcome")
    void testIncrementCounters() {
        metricsService.recordSuccess(45);
        metricsService.recordSuccess(60);
        metricsService.recordFailure(120);
        metricsService.recordTimeout(5000);
        metricsService.recordDeadLetter();

        Counter successCounter = registry.find("eventflow.deliveries.total")
                .tag("status", "success")
                .counter();
        Counter failureCounter = registry.find("eventflow.deliveries.total")
                .tag("status", "failure")
                .counter();
        Counter timeoutCounter = registry.find("eventflow.deliveries.total")
                .tag("status", "timeout")
                .counter();
        Counter dlqCounter = registry.find("eventflow.deliveries.dlq")
                .counter();

        assertEquals(2.0, successCounter.count(), 0.001);
        assertEquals(1.0, failureCounter.count(), 0.001);
        assertEquals(1.0, timeoutCounter.count(), 0.001);
        assertEquals(1.0, dlqCounter.count(), 0.001);
    }

    @Test
    @DisplayName("Should record latency percentiles in dispatch timer")
    void testDispatchTimer() {
        metricsService.recordSuccess(100);
        metricsService.recordSuccess(200);

        Timer timer = registry.find("eventflow.deliveries.duration").timer();
        assertEquals(2, timer.count());
        assertTrue(timer.totalTime(TimeUnit.MILLISECONDS) >= 300);
    }
}
