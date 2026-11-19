package com.eventflow.dto;

import com.eventflow.entity.DeliveryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EventDto {

    public record IngestRequest(
        @NotBlank(message = "event_type is required")
        String eventType,

        @NotNull(message = "payload is required")
        Map<String, Object> payload,

        String idempotencyKey
    ) {}

    public record IngestResponse(
        UUID eventId,
        String eventType,
        String status,
        int deliveriesScheduled,
        Instant createdAt
    ) {}

    public record EventDetailResponse(
        UUID id,
        String eventType,
        String payload,
        String idempotencyKey,
        Instant createdAt,
        List<DeliverySummary> deliveries
    ) {}

    public record DeliverySummary(
        UUID id,
        UUID endpointId,
        String endpointUrl,
        DeliveryStatus status,
        int attemptCount,
        Instant nextRetryAt
    ) {}
}
