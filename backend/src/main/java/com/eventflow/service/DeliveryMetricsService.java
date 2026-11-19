package com.eventflow.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class DeliveryMetricsService {

    private final MeterRegistry registry;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter timeoutCounter;
    private final Counter dlqCounter;
    private final Timer dispatchTimer;

    public DeliveryMetricsService(MeterRegistry registry) {
        this.registry = registry;
        this.successCounter = Counter.builder("eventflow.deliveries.total")
                .tag("status", "success")
                .description("Total successful webhook deliveries")
                .register(registry);

        this.failureCounter = Counter.builder("eventflow.deliveries.total")
                .tag("status", "failure")
                .description("Total failed webhook deliveries")
                .register(registry);

        this.timeoutCounter = Counter.builder("eventflow.deliveries.total")
                .tag("status", "timeout")
                .description("Total timed-out webhook deliveries")
                .register(registry);

        this.dlqCounter = Counter.builder("eventflow.deliveries.dlq")
                .description("Total webhook deliveries exhausted and moved to dead-letter queue")
                .register(registry);

        this.dispatchTimer = Timer.builder("eventflow.deliveries.duration")
                .description("Latency distribution of outbound webhook dispatches")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(registry);
    }

    public void recordSuccess(long durationMs) {
        successCounter.increment();
        dispatchTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordFailure(long durationMs) {
        failureCounter.increment();
        dispatchTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordTimeout(long durationMs) {
        timeoutCounter.increment();
        dispatchTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordDeadLetter() {
        dlqCounter.increment();
    }
}
