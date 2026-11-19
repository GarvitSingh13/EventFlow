package com.eventflow.service;

import com.eventflow.dto.DeliveryDto;
import com.eventflow.entity.DeliveryAttempt;
import com.eventflow.entity.DeliveryStatus;
import com.eventflow.entity.EventDelivery;
import com.eventflow.repository.DeliveryAttemptRepository;
import com.eventflow.repository.EventDeliveryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeliveryService {

    private final EventDeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository attemptRepository;

    public DeliveryService(
            EventDeliveryRepository deliveryRepository,
            DeliveryAttemptRepository attemptRepository) {
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
    }

    @Transactional(readOnly = true)
    public Page<DeliveryDto.DetailResponse> listDeliveries(UUID organizationId, Pageable pageable) {
        return deliveryRepository.findByEndpointOrganizationIdOrderByCreatedAtDesc(organizationId, pageable)
                .map(this::mapToDetail);
    }

    @Transactional(readOnly = true)
    public DeliveryDto.DetailResponse getDelivery(UUID organizationId, UUID deliveryId) {
        EventDelivery delivery = findDeliveryOrThrow(organizationId, deliveryId);
        return mapToDetail(delivery);
    }

    @Transactional
    public DeliveryDto.DetailResponse retryDelivery(UUID organizationId, UUID deliveryId) {
        EventDelivery delivery = findDeliveryOrThrow(organizationId, deliveryId);

        // Reset to PENDING and schedule immediate delivery
        delivery.setStatus(DeliveryStatus.PENDING);
        delivery.setNextRetryAt(Instant.now());
        delivery.setLockedAt(null);
        delivery.setLockedBy(null);

        delivery = deliveryRepository.save(delivery);
        return mapToDetail(delivery);
    }

    @Transactional(readOnly = true)
    public Page<DeliveryDto.DetailResponse> listDlqDeliveries(UUID organizationId, Pageable pageable) {
        return deliveryRepository.findByEndpointOrganizationIdAndStatusOrderByCreatedAtDesc(
                organizationId, DeliveryStatus.DEAD_LETTER, pageable)
                .map(this::mapToDetail);
    }

    @Transactional
    public int bulkReplayDlq(UUID organizationId, List<UUID> deliveryIds) {
        List<EventDelivery> dlqItems;
        if (deliveryIds == null || deliveryIds.isEmpty()) {
            dlqItems = deliveryRepository.findByEndpointOrganizationIdAndStatus(organizationId, DeliveryStatus.DEAD_LETTER);
        } else {
            dlqItems = deliveryRepository.findByIdInAndEndpointOrganizationId(deliveryIds, organizationId).stream()
                    .filter(d -> d.getStatus() == DeliveryStatus.DEAD_LETTER)
                    .toList();
        }

        Instant now = Instant.now();
        for (EventDelivery delivery : dlqItems) {
            delivery.setStatus(DeliveryStatus.PENDING);
            delivery.setAttemptCount(0);
            delivery.setNextRetryAt(now);
            delivery.setLockedAt(null);
            delivery.setLockedBy(null);
        }
        deliveryRepository.saveAll(dlqItems);
        return dlqItems.size();
    }

    @Transactional(readOnly = true)
    public DeliveryDto.StatsResponse getStats(UUID organizationId) {
        long delivered = deliveryRepository.countByEndpointOrganizationIdAndStatus(organizationId, DeliveryStatus.DELIVERED);
        long pending = deliveryRepository.countByEndpointOrganizationIdAndStatus(organizationId, DeliveryStatus.PENDING);
        long delivering = deliveryRepository.countByEndpointOrganizationIdAndStatus(organizationId, DeliveryStatus.DELIVERING);
        long retrying = deliveryRepository.countByEndpointOrganizationIdAndStatus(organizationId, DeliveryStatus.RETRY_SCHEDULED);
        long deadLetter = deliveryRepository.countByEndpointOrganizationIdAndStatus(organizationId, DeliveryStatus.DEAD_LETTER);
        long total = delivered + pending + delivering + retrying + deadLetter;

        return new DeliveryDto.StatsResponse(total, delivered, pending, delivering, retrying, deadLetter);
    }

    private EventDelivery findDeliveryOrThrow(UUID organizationId, UUID deliveryId) {
        EventDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found"));

        if (!delivery.getEndpoint().getOrganization().getId().equals(organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this delivery record");
        }
        return delivery;
    }

    public DeliveryDto.DetailResponse mapToDetail(EventDelivery d) {
        List<DeliveryAttempt> attempts = attemptRepository.findByDeliveryIdOrderByAttemptNumberAsc(d.getId());
        List<DeliveryDto.AttemptResponse> attemptDtos = attempts.stream()
                .map(a -> new DeliveryDto.AttemptResponse(
                        a.getId(),
                        a.getAttemptNumber(),
                        a.getResponseStatusCode(),
                        a.getResponseHeaders(),
                        a.getResponseBody(),
                        a.getExecutionTimeMs(),
                        a.getErrorMessage(),
                        a.getExecutedAt()
                ))
                .toList();

        return new DeliveryDto.DetailResponse(
                d.getId(),
                d.getEvent().getId(),
                d.getEvent().getEventType(),
                d.getEvent().getPayload(),
                d.getEndpoint().getId(),
                d.getEndpoint().getUrl(),
                d.getStatus(),
                d.getAttemptCount(),
                d.getMaxAttempts(),
                d.getNextRetryAt(),
                d.getCreatedAt(),
                attemptDtos
        );
    }
}
