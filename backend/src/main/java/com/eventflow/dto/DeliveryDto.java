package com.eventflow.dto;

import com.eventflow.entity.DeliveryStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class DeliveryDto {

    public record DetailResponse(
        UUID id,
        UUID eventId,
        String eventType,
        String eventPayload,
        UUID endpointId,
        String endpointUrl,
        DeliveryStatus status,
        int attemptCount,
        int maxAttempts,
        Instant nextRetryAt,
        Instant createdAt,
        List<AttemptResponse> attempts
    ) {}

    public record AttemptResponse(
        UUID id,
        int attemptNumber,
        Integer responseStatusCode,
        String responseHeaders,
        String responseBody,
        Integer executionTimeMs,
        String errorMessage,
        Instant executedAt
    ) {}

    public record StatsResponse(
        long totalDeliveries,
        long delivered,
        long pending,
        long delivering,
        long retrying,
        long deadLetter
    ) {}
}
