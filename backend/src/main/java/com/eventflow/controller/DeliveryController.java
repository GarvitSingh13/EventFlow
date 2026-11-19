package com.eventflow.controller;

import com.eventflow.dto.DeliveryDto;
import com.eventflow.security.SecurityUtils;
import com.eventflow.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
@Tag(name = "Delivery Logs & Replay", description = "Audit logs, attempt inspection, and manual delivery retry")
@SecurityRequirement(name = "bearerAuth")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
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
}
