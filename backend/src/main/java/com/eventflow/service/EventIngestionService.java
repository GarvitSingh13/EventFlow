package com.eventflow.service;

import com.eventflow.dto.EventDto;
import com.eventflow.entity.Event;
import com.eventflow.entity.EventDelivery;
import com.eventflow.entity.Organization;
import com.eventflow.entity.WebhookEndpoint;
import com.eventflow.repository.EventDeliveryRepository;
import com.eventflow.repository.EventRepository;
import com.eventflow.repository.OrganizationRepository;
import com.eventflow.repository.WebhookEndpointRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EventIngestionService {

    private final EventRepository eventRepository;
    private final EventDeliveryRepository deliveryRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final OrganizationRepository organizationRepository;
    private final ObjectMapper objectMapper;

    public EventIngestionService(
            EventRepository eventRepository,
            EventDeliveryRepository deliveryRepository,
            WebhookEndpointRepository endpointRepository,
            OrganizationRepository organizationRepository,
            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.deliveryRepository = deliveryRepository;
        this.endpointRepository = endpointRepository;
        this.organizationRepository = organizationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EventDto.IngestResponse ingestEvent(UUID organizationId, EventDto.IngestRequest request) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        // 1. Idempotency Check
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<Event> existingEvent = eventRepository.findByOrganizationIdAndIdempotencyKey(
                    organizationId, request.idempotencyKey().trim());
            if (existingEvent.isPresent()) {
                Event ev = existingEvent.get();
                List<EventDelivery> existingDeliveries = deliveryRepository.findByEventId(ev.getId());
                return new EventDto.IngestResponse(
                        ev.getId(),
                        ev.getEventType(),
                        "DUPLICATE_ACCEPTED",
                        existingDeliveries.size(),
                        ev.getCreatedAt()
                );
            }
        }

        // 2. Serialize payload to JSON string
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(request.payload());
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload structure", e);
        }

        // 3. Persist Event
        Event event = new Event(
                org,
                request.eventType().trim(),
                payloadJson,
                request.idempotencyKey() != null ? request.idempotencyKey().trim() : null
        );
        event = eventRepository.save(event);

        // 4. Find matching subscribed ACTIVE endpoints
        List<WebhookEndpoint> activeEndpoints = endpointRepository.findByOrganizationIdAndStatus(organizationId, "ACTIVE");
        List<EventDelivery> deliveries = new ArrayList<>();

        for (WebhookEndpoint endpoint : activeEndpoints) {
            if (endpoint.isSubscribedTo(event.getEventType())) {
                EventDelivery delivery = new EventDelivery(event, endpoint);
                deliveries.add(delivery);
            }
        }

        if (!deliveries.isEmpty()) {
            deliveryRepository.saveAll(deliveries);
        }

        return new EventDto.IngestResponse(
                event.getId(),
                event.getEventType(),
                "ACCEPTED",
                deliveries.size(),
                event.getCreatedAt()
        );
    }
}
