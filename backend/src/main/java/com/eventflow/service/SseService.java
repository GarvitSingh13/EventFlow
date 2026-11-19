package com.eventflow.service;

import com.eventflow.dto.DeliveryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);

    // Map organizationId -> list of active SseEmitters
    private final Map<UUID, List<SseEmitter>> emittersByOrg = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID organizationId) {
        // 30 minute timeout for live developer console sessions
        SseEmitter emitter = new SseEmitter(1800000L);

        emittersByOrg.computeIfAbsent(organizationId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(organizationId, emitter));
        emitter.onTimeout(() -> removeEmitter(organizationId, emitter));
        emitter.onError(e -> removeEmitter(organizationId, emitter));

        try {
            // Send initial ping to establish connection
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data("Connected to EventFlow Real-Time Delivery Stream"));
        } catch (IOException e) {
            removeEmitter(organizationId, emitter);
        }

        return emitter;
    }

    public void broadcastDeliveryUpdate(UUID organizationId, DeliveryDto.DetailResponse delivery) {
        List<SseEmitter> emitters = emittersByOrg.get(organizationId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("DELIVERY_UPDATE")
                        .data(delivery));
            } catch (Exception e) {
                removeEmitter(organizationId, emitter);
            }
        }
    }

    private void removeEmitter(UUID organizationId, SseEmitter emitter) {
        List<SseEmitter> list = emittersByOrg.get(organizationId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emittersByOrg.remove(organizationId);
            }
        }
    }
}
