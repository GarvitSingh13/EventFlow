package com.eventflow.controller;

import com.eventflow.dto.DeliveryDto;
import com.eventflow.security.SecurityUtils;
import com.eventflow.service.DeliveryService;
import com.eventflow.service.SseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
@Tag(name = "Delivery Logs & Replay", description = "Audit logs, attempt inspection, real-time SSE streaming, and DLQ management")
@SecurityRequirement(name = "bearerAuth")
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final SseService sseService;

    public DeliveryController(DeliveryService deliveryService, SseService sseService) {
        this.deliveryService = deliveryService;
        this.sseService = sseService;
    }

    @GetMapping
    @Operation(summary = "List deliveries with pagination and attempt history")
    public ResponseEntity<Page<DeliveryDto.DetailResponse>> listDeliveries(
            @PageableDefault(size = 20) Pageable pageable) {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        return ResponseEntity.ok(deliveryService.listDeliveries(orgId, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get single delivery details with all historical attempts")
    public ResponseEntity<DeliveryDto.DetailResponse> getDelivery(@PathVariable UUID id) {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        return ResponseEntity.ok(deliveryService.getDelivery(orgId, id));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Manually trigger immediate retry of a failed or dead-letter delivery")
    public ResponseEntity<DeliveryDto.DetailResponse> retryDelivery(@PathVariable UUID id) {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        return ResponseEntity.ok(deliveryService.retryDelivery(orgId, id));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get delivery breakdown metrics (delivered, pending, retrying, dead_letter)")
    public ResponseEntity<DeliveryDto.StatsResponse> getStats() {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        return ResponseEntity.ok(deliveryService.getStats(orgId));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Real-time Server-Sent Events (SSE) stream for live delivery notifications")
    public SseEmitter subscribeToDeliveryStream() {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        return sseService.subscribe(orgId);
    }

    @GetMapping("/dlq")
    @Operation(summary = "List all deliveries currently in Dead-Letter Queue (exhausted max retries)")
    public ResponseEntity<Page<DeliveryDto.DetailResponse>> listDlq(
            @PageableDefault(size = 20) Pageable pageable) {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        return ResponseEntity.ok(deliveryService.listDlqDeliveries(orgId, pageable));
    }

    @PostMapping("/dlq/replay")
    @Operation(summary = "Bulk replay dead-lettered deliveries (resetting them to PENDING for immediate dispatch)")
    public ResponseEntity<Map<String, Object>> bulkReplayDlq(
            @RequestBody(required = false) Map<String, List<UUID>> requestBody) {
        UUID orgId = SecurityUtils.getCurrentOrganizationId();
        List<UUID> deliveryIds = (requestBody != null) ? requestBody.get("deliveryIds") : null;
        int count = deliveryService.bulkReplayDlq(orgId, deliveryIds);
        return ResponseEntity.ok(Map.of(
                "replayedCount", count,
                "message", "Successfully queued " + count + " dead-letter deliveries for redelivery"
        ));
    }
}
