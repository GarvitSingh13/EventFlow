package com.eventflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.hibernate.validator.constraints.URL;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class WebhookEndpointDto {

    public record CreateRequest(
        @NotBlank(message = "Webhook URL is required")
        @URL(message = "Must be a valid URL")
        String url,

        String description,

        @NotEmpty(message = "At least one event type or '*' is required")
        List<String> eventTypes,

        Integer rateLimit,
        Integer timeoutMs
    ) {}

    public record UpdateRequest(
        @URL(message = "Must be a valid URL")
        String url,

        String description,
        List<String> eventTypes,
        String status, // ACTIVE, PAUSED, DISABLED
        Integer rateLimit,
        Integer timeoutMs
    ) {}

    public record Response(
        UUID id,
        String url,
        String description,
        String secretToken,
        List<String> eventTypes,
        String status,
        int rateLimit,
        int timeoutMs,
        Instant createdAt
    ) {}
}
