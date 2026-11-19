package com.eventflow.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public class ApiKeyDto {

    public record CreateRequest(
        @NotBlank(message = "API key name is required")
        String name,
        Instant expiresAt
    ) {}

    public record Response(
        UUID id,
        String name,
        String prefix,
        String status,
        Instant expiresAt,
        Instant createdAt
    ) {}

    public record CreatedResponse(
        UUID id,
        String name,
        String prefix,
        String rawApiKey, // Only returned once upon creation!
        Instant expiresAt,
        Instant createdAt
    ) {}
}
