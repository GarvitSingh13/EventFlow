package com.eventflow.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "dGhpc19pc19hX3Zlcnlfc2VjdXJlX2FuZF9sb25nXzI1NmJpdF9qd3Rfc2VjcmV0X2tleV9mb3JfZXZlbnRmbG93XzIwMjQ=";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret, 3600000L); // 1 hour
    }

    @Test
    @DisplayName("Should generate valid JWT and extract correct claims")
    void testGenerateAndValidateToken() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String email = "dev@eventflow.io";
        String role = "ADMIN";

        String token = jwtService.generateToken(userId, orgId, email, role);

        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token));

        Claims claims = jwtService.extractClaims(token);
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals(orgId.toString(), claims.get("organizationId", String.class));
        assertEquals(email, claims.get("email", String.class));
        assertEquals(role, claims.get("role", String.class));
    }

    @Test
    @DisplayName("Expired token must be reported as invalid")
    void testExpiredToken() {
        JwtService shortLivedService = new JwtService(secret, -1000L); // already expired
        String token = shortLivedService.generateToken(UUID.randomUUID(), UUID.randomUUID(), "test@test.com", "USER");

        assertFalse(shortLivedService.isTokenValid(token));
    }
}
