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
    public ResponseEntity<EventDto.IngestResponse> ingestEvent(@Valid @RequestBody EventDto.IngestRequest request) {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        EventDto.IngestResponse response = ingestionService.ingestEvent(orgId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
