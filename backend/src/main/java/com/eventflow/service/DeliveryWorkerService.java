package com.eventflow.service;

import com.eventflow.entity.DeliveryAttempt;
import com.eventflow.entity.DeliveryStatus;
import com.eventflow.entity.EventDelivery;
import com.eventflow.entity.WebhookEndpoint;
import com.eventflow.repository.DeliveryAttemptRepository;
import com.eventflow.repository.EventDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DeliveryWorkerService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryWorkerService.class);

    private final EventDeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final HmacService hmacService;
    private final HttpClient httpClient;

    private final String workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);

    @Value("${eventflow.worker.batch-size:50}")
    private int batchSize;

    @Value("${eventflow.worker.base-retry-delay-seconds:2}")
    private long baseDelaySeconds;

    @Value("${eventflow.worker.max-retry-delay-seconds:3600}")
    private long maxDelaySeconds;

    public DeliveryWorkerService(
            EventDeliveryRepository deliveryRepository,
            DeliveryAttemptRepository attemptRepository,
            HmacService hmacService) {
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
        this.hmacService = hmacService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Scheduled(fixedDelayString = "${eventflow.worker.polling-interval-ms:2000}")
    public void pollAndDispatchDeliveries() {
        try {
            processBatch();
        } catch (Exception e) {
            log.error("Unexpected error in delivery worker loop", e);
        }
    }

    @Transactional
    public void processBatch() {
        Instant now = Instant.now();
        List<EventDelivery> dueDeliveries = deliveryRepository.findAndLockDueDeliveries(now, batchSize);

        if (dueDeliveries.isEmpty()) {
            return;
        }

        log.info("[{}] Claimed {} due deliveries with SKIP LOCKED", workerId, dueDeliveries.size());

        for (EventDelivery delivery : dueDeliveries) {
            delivery.setStatus(DeliveryStatus.DELIVERING);
            delivery.setLockedAt(now);
            delivery.setLockedBy(workerId);
        }
        deliveryRepository.saveAll(dueDeliveries);

        // Process each locked delivery
        for (EventDelivery delivery : dueDeliveries) {
            try {
                executeDelivery(delivery);
            } catch (Exception e) {
                log.error("Error executing delivery " + delivery.getId(), e);
            }
        }
    }

    private void executeDelivery(EventDelivery delivery) {
        WebhookEndpoint endpoint = delivery.getEndpoint();
        String payload = delivery.getEvent().getPayload();
        long timestamp = Instant.now().getEpochSecond();
        String signatureHeader = hmacService.formatSignatureHeader(endpoint.getSecretToken(), timestamp, payload);

        int timeoutMs = endpoint.getTimeoutMs() > 0 ? endpoint.getTimeoutMs() : 5000;
        int nextAttemptNumber = delivery.getAttemptCount() + 1;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint.getUrl()))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("User-Agent", "EventFlow-Webhook-Dispatcher/1.0")
                .header("X-EventFlow-Delivery", delivery.getId().toString())
                .header("X-EventFlow-Event", delivery.getEvent().getEventType())
                .header("X-EventFlow-Timestamp", String.valueOf(timestamp))
                .header("X-EventFlow-Signature", signatureHeader)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        long startTime = System.currentTimeMillis();
        Integer statusCode = null;
        String responseHeaders = null;
        String responseBody = null;
        String errorMessage = null;

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            responseHeaders = response.headers().map().toString();
            String fullBody = response.body();
            // Truncate response body snippet to 2048 chars to prevent DB bloat
            if (fullBody != null) {
                responseBody = fullBody.length() > 2048 ? fullBody.substring(0, 2048) + "... [truncated]" : fullBody;
            }
        } catch (HttpTimeoutException e) {
            errorMessage = "HTTP Request Timed Out after " + timeoutMs + "ms";
        } catch (IOException e) {
            errorMessage = "I/O Connection Error: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errorMessage = "Dispatch interrupted: " + e.getMessage();
        } catch (Exception e) {
            errorMessage = "Unexpected Error: " + e.getMessage();
        }

        int durationMs = (int) (System.currentTimeMillis() - startTime);

        // Record attempt log
        DeliveryAttempt attempt = new DeliveryAttempt(
                delivery,
                nextAttemptNumber,
                statusCode,
                responseHeaders,
                responseBody,
                durationMs,
                errorMessage
        );
        attemptRepository.save(attempt);

        // Update delivery state
        delivery.setAttemptCount(nextAttemptNumber);
        delivery.setLockedAt(null);
        delivery.setLockedBy(null);

        boolean isSuccess = statusCode != null && statusCode >= 200 && statusCode < 300;

        if (isSuccess) {
            delivery.setStatus(DeliveryStatus.DELIVERED);
            log.info("Delivery [{}] SUCCEEDED on attempt {} (Status: {}, Latency: {}ms)",
                    delivery.getId(), nextAttemptNumber, statusCode, durationMs);
        } else {
            if (nextAttemptNumber < delivery.getMaxAttempts()) {
                long backoffSeconds = calculateFullJitterDelay(nextAttemptNumber);
                Instant nextRetry = Instant.now().plusSeconds(backoffSeconds);
                delivery.setStatus(DeliveryStatus.RETRY_SCHEDULED);
                delivery.setNextRetryAt(nextRetry);
                log.warn("Delivery [{}] FAILED on attempt {} (Status: {}, Error: {}). Retrying in {}s at {}",
                        delivery.getId(), nextAttemptNumber, statusCode, errorMessage, backoffSeconds, nextRetry);
            } else {
                delivery.setStatus(DeliveryStatus.DEAD_LETTER);
                log.error("Delivery [{}] EXHAUSTED all {} attempts. Moved to DEAD_LETTER (Status: {}, Error: {})",
                        delivery.getId(), delivery.getMaxAttempts(), statusCode, errorMessage);
            }
        }

        deliveryRepository.save(delivery);
    }

    /**
     * Exponential Backoff with Full Jitter:
     * temp = min(maxDelay, baseDelay * 2^(attempt))
     * delay = uniform_random(0, temp)
     */
    public long calculateFullJitterDelay(int attempt) {
        long exponentialBase = baseDelaySeconds * (1L << Math.min(attempt, 20));
        long ceiling = Math.min(maxDelaySeconds, exponentialBase);
        if (ceiling <= 1) {
            return 1;
        }
        return ThreadLocalRandom.current().nextLong(1, ceiling + 1);
    }
}
