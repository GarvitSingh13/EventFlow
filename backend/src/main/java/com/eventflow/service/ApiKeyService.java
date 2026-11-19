package com.eventflow.service;

import com.eventflow.dto.ApiKeyDto;
import com.eventflow.entity.ApiKey;
import com.eventflow.entity.Organization;
import com.eventflow.repository.ApiKeyRepository;
import com.eventflow.repository.OrganizationRepository;
import com.eventflow.security.SecurityAuthenticationFilter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final OrganizationRepository organizationRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository, OrganizationRepository organizationRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public ApiKeyDto.CreatedResponse createApiKey(UUID organizationId, ApiKeyDto.CreateRequest request) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        String randomHex = HexFormat.of().formatHex(randomBytes);
        String rawKey = "ef_live_" + randomHex;
        String prefix = "ef_live_" + randomHex.substring(0, 4);
        String keyHash = SecurityAuthenticationFilter.hashApiKey(rawKey);

        ApiKey apiKey = new ApiKey(org, request.name(), keyHash, prefix, request.expiresAt());
        apiKey = apiKeyRepository.save(apiKey);

        return new ApiKeyDto.CreatedResponse(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getPrefix(),
                rawKey,
                apiKey.getExpiresAt(),
                apiKey.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ApiKeyDto.Response> listApiKeys(UUID organizationId) {
        return apiKeyRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .map(k -> new ApiKeyDto.Response(
                        k.getId(),
                        k.getName(),
                        k.getPrefix(),
                        k.getStatus(),
                        k.getExpiresAt(),
                        k.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public void revokeApiKey(UUID organizationId, UUID keyId) {
        ApiKey apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API Key not found"));

        if (!apiKey.getOrganization().getId().equals(organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this API key");
        }

        apiKey.setStatus("REVOKED");
        apiKeyRepository.save(apiKey);
    }
}
