package com.eventflow.security;

import com.eventflow.entity.ApiKey;
import com.eventflow.repository.ApiKeyRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Component
public class SecurityAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ApiKeyRepository apiKeyRepository;

    public SecurityAuthenticationFilter(JwtService jwtService, ApiKeyRepository apiKeyRepository) {
        this.jwtService = jwtService;
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String apiKeyHeader = request.getHeader("X-API-Key");

        // 1. Check for JWT Bearer token
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtService.isTokenValid(token)) {
                Claims claims = jwtService.extractClaims(token);
                UUID userId = UUID.fromString(claims.getSubject());
                UUID orgId = UUID.fromString(claims.get("organizationId", String.class));
                String email = claims.get("email", String.class);
                String role = claims.get("role", String.class);

                SecurityUser userDetails = new SecurityUser(userId, orgId, email, role);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } 
        // 2. Check for X-API-Key header (Machine-to-Machine)
        else if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            String keyHash = hashApiKey(apiKeyHeader.trim());
            Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyHashAndStatus(keyHash, "ACTIVE");

            if (apiKeyOpt.isPresent()) {
                ApiKey apiKey = apiKeyOpt.get();
                if (apiKey.getExpiresAt() == null || apiKey.getExpiresAt().isAfter(Instant.now())) {
                    ApiKeyAuthentication auth = new ApiKeyAuthentication(
                            apiKey.getOrganization().getId(),
                            apiKey.getName()
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    public static String hashApiKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
