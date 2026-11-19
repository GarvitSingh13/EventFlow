package com.eventflow.controller;

import com.eventflow.dto.EventDto;
import com.eventflow.security.SecurityUtils;
import com.eventflow.service.EventIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Event Ingestion", description = "Ingest events from upstream microservices for delivery")
public class EventController {

    private final EventIngestionService ingestionService;

    public EventController(EventIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    @Operation(
        summary = "Ingest an event for delivery to subscribed endpoints",
        security = { @SecurityRequirement(name = "apiKeyAuth"), @SecurityRequirement(name = "bearerAuth") }
    )
    public ResponseEntity<EventDto.IngestResponse> ingestEvent(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKeyHeader,
            @Valid @RequestBody EventDto.IngestRequest request) {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();

        String effectiveKey = request.idempotencyKey();
        if ((effectiveKey == null || effectiveKey.isBlank()) && idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            effectiveKey = idempotencyKeyHeader.trim();
        } else if ((effectiveKey == null || effectiveKey.isBlank()) && xIdempotencyKeyHeader != null && !xIdempotencyKeyHeader.isBlank()) {
            effectiveKey = xIdempotencyKeyHeader.trim();
        }

        EventDto.IngestRequest effectiveRequest = new EventDto.IngestRequest(
                request.eventType(),
                request.payload(),
                effectiveKey
        );

        EventDto.IngestResponse response = ingestionService.ingestEvent(orgId, effectiveRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
