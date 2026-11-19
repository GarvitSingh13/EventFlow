package com.eventflow.service;

import com.eventflow.dto.WebhookEndpointDto;
import com.eventflow.entity.Organization;
import com.eventflow.entity.WebhookEndpoint;
import com.eventflow.repository.OrganizationRepository;
import com.eventflow.repository.WebhookEndpointRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class WebhookEndpointService {

    private final WebhookEndpointRepository endpointRepository;
    private final OrganizationRepository organizationRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebhookEndpointService(
            WebhookEndpointRepository endpointRepository,
            OrganizationRepository organizationRepository) {
        this.endpointRepository = endpointRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public WebhookEndpointDto.Response createEndpoint(UUID organizationId, WebhookEndpointDto.CreateRequest request) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        byte[] secretBytes = new byte[24];
        secureRandom.nextBytes(secretBytes);
        String secretToken = "whsec_" + HexFormat.of().formatHex(secretBytes);

        WebhookEndpoint endpoint = new WebhookEndpoint(
                org,
                request.url().trim(),
                request.description(),
                secretToken,
                request.eventTypes(),
                request.rateLimit() != null ? request.rateLimit() : 100,
                request.timeoutMs() != null ? request.timeoutMs() : 5000
        );
        endpoint = endpointRepository.save(endpoint);
        return mapToResponse(endpoint);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointDto.Response> listEndpoints(UUID organizationId) {
        return endpointRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WebhookEndpointDto.Response getEndpoint(UUID organizationId, UUID endpointId) {
        WebhookEndpoint endpoint = findEndpointOrThrow(organizationId, endpointId);
        return mapToResponse(endpoint);
    }

    @Transactional
    public WebhookEndpointDto.Response updateEndpoint(
            UUID organizationId, UUID endpointId, WebhookEndpointDto.UpdateRequest request) {
        WebhookEndpoint endpoint = findEndpointOrThrow(organizationId, endpointId);

        if (request.url() != null && !request.url().isBlank()) {
            endpoint.setUrl(request.url().trim());
        }
        if (request.description() != null) {
            endpoint.setDescription(request.description());
        }
        if (request.eventTypes() != null && !request.eventTypes().isEmpty()) {
            endpoint.setEventTypes(request.eventTypes());
        }
        if (request.status() != null && !request.status().isBlank()) {
            endpoint.setStatus(request.status().toUpperCase());
        }
        if (request.rateLimit() != null && request.rateLimit() > 0) {
            endpoint.setRateLimit(request.rateLimit());
        }
        if (request.timeoutMs() != null && request.timeoutMs() > 0) {
            endpoint.setTimeoutMs(request.timeoutMs());
        }

        endpoint = endpointRepository.save(endpoint);
        return mapToResponse(endpoint);
    }

    @Transactional
    public void deleteEndpoint(UUID organizationId, UUID endpointId) {
        WebhookEndpoint endpoint = findEndpointOrThrow(organizationId, endpointId);
        endpointRepository.delete(endpoint);
    }

    private WebhookEndpoint findEndpointOrThrow(UUID organizationId, UUID endpointId) {
        WebhookEndpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook endpoint not found"));

        if (!endpoint.getOrganization().getId().equals(organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this endpoint");
        }
        return endpoint;
    }

    private WebhookEndpointDto.Response mapToResponse(WebhookEndpoint e) {
        return new WebhookEndpointDto.Response(
                e.getId(),
                e.getUrl(),
                e.getDescription(),
                e.getSecretToken(),
                e.getEventTypes(),
                e.getStatus(),
                e.getRateLimit(),
                e.getTimeoutMs(),
                e.getCreatedAt()
        );
    }
}
