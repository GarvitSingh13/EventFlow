package com.eventflow.controller;

import com.eventflow.dto.ApiKeyDto;
import com.eventflow.security.SecurityUtils;
import com.eventflow.service.ApiKeyService;
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
@RequestMapping("/api/v1/api-keys")
@Tag(name = "API Keys", description = "Management of machine-to-machine event ingestion credentials")
@SecurityRequirement(name = "bearerAuth")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @Operation(summary = "Generate a new API key (Raw secret returned once)")
    public ResponseEntity<ApiKeyDto.CreatedResponse> createApiKey(@Valid @RequestBody ApiKeyDto.CreateRequest request) {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        ApiKeyDto.CreatedResponse response = apiKeyService.createApiKey(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all API keys for current organization")
    public ResponseEntity<List<ApiKeyDto.Response>> listApiKeys() {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        return ResponseEntity.ok(apiKeyService.listApiKeys(orgId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke an API key")
    public ResponseEntity<Void> revokeApiKey(@PathVariable UUID id) {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        apiKeyService.revokeApiKey(orgId, id);
        return ResponseEntity.noContent().build();
    }
}
