package com.brokerdealer.notificationservice.service;

import com.brokerdealer.notificationservice.dto.response.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final String CLIENT_DESTINATION = "/topic/notifications/";
    private static final String BROADCAST_DESTINATION = "/topic/notifications/broadcast";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Push a notification to a specific client's WebSocket subscription.
     * UI subscribes to /topic/notifications/{clientId}.
     */
    public void pushToClient(UUID clientId, NotificationResponse notification) {
        String destination = CLIENT_DESTINATION + clientId;
        log.info("Pushing notification: type={} clientId={} destination={}",
                notification.getType(), clientId, destination);
        messagingTemplate.convertAndSend(destination, notification);
    }

    /**
     * Broadcast a notification to all connected OPERATIONS/ADMIN subscribers.
     * UI subscribes to /topic/notifications/broadcast.
     */
    public void broadcast(NotificationResponse notification) {
        log.info("Broadcasting notification: type={} destination={}", notification.getType(), BROADCAST_DESTINATION);
        messagingTemplate.convertAndSend(BROADCAST_DESTINATION, notification);
    }
}