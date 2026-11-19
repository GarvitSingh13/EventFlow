package com.eventflow.controller;

import com.eventflow.dto.WebhookEndpointDto;
import com.eventflow.security.SecurityUtils;
import com.eventflow.service.WebhookEndpointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/endpoints")
@Tag(name = "Webhook Endpoints", description = "Registration and management of destination webhook targets")
@SecurityRequirement(name = "bearerAuth")
public class WebhookEndpointController {

    private final WebhookEndpointService endpointService;

    public WebhookEndpointController(WebhookEndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @PostMapping
    @Operation(summary = "Register a new webhook subscriber endpoint")
    public ResponseEntity<WebhookEndpointDto.Response> createEndpoint(
            @Valid @RequestBody WebhookEndpointDto.CreateRequest request) {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        WebhookEndpointDto.Response response = endpointService.createEndpoint(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all webhook endpoints for current organization")
    public ResponseEntity<List<WebhookEndpointDto.Response>> listEndpoints() {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        return ResponseEntity.ok(endpointService.listEndpoints(orgId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get webhook endpoint details")
    public ResponseEntity<WebhookEndpointDto.Response> getEndpoint(@PathVariable UUID id) {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        return ResponseEntity.ok(endpointService.getEndpoint(orgId, id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update webhook endpoint settings or event subscriptions")
    public ResponseEntity<WebhookEndpointDto.Response> updateEndpoint(
            @PathVariable UUID id,
            @Valid @RequestBody WebhookEndpointDto.UpdateRequest request) {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        return ResponseEntity.ok(endpointService.updateEndpoint(orgId, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a webhook endpoint")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable UUID id) {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        endpointService.deleteEndpoint(orgId, id);
        return ResponseEntity.noContent().build();
    }
}
