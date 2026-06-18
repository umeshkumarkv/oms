package com.brokerdealer.notificationservice.service;

import com.brokerdealer.notificationservice.dto.response.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SseEmitterRegistry sseEmitterRegistry;

    /**
     * Push a notification to a specific TRADER's SSE stream.
     */
    public void pushToClient(UUID clientId, NotificationResponse notification) {
        log.info("Pushing notification: type={} clientId={}", notification.getType(), clientId);
        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name(notification.getType().name())
                .data(notification);
        sseEmitterRegistry.pushToUser(clientId, event);
    }

    /**
     * Broadcast a notification to all OPERATIONS/ADMIN SSE streams.
     */
    public void broadcast(NotificationResponse notification) {
        log.info("Broadcasting notification: type={}", notification.getType());
        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name(notification.getType().name())
                .data(notification);
        sseEmitterRegistry.broadcast(event);
    }
}
