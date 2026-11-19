package com.eventflow.dto;

import java.util.UUID;

public record AuthResponse(
    String token,
    UUID userId,
    UUID organizationId,
    String organizationName,
    String email,
    String role
) {}
