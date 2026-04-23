package com.brokerdealer.notificationservice.kafka.consumer;

import com.brokerdealer.notificationservice.domain.NotificationType;
import com.brokerdealer.notificationservice.dto.response.NotificationResponse;
import com.brokerdealer.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStateConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "order.state.changed",
                   groupId = "notification-service-order.state.changed-group")
    public void consumeOrderStateChanged(Object event) {
        // TODO: implement — JIRA story BD-XX
        // Expected payload fields: orderId, clientId, previousState, newState, actorId, actorType, timestamp
        log.info("Stub: OrderStateConsumer.consumeOrderStateChanged received message");

        // Skeleton push — replace with real event deserialization in implementation story
        if (event instanceof Map<?, ?> payload) {
            try {
                UUID clientId = UUID.fromString(String.valueOf(payload.get("clientId")));
                UUID orderId  = UUID.fromString(String.valueOf(payload.get("orderId")));
                String newState = String.valueOf(payload.get("newState"));

                NotificationResponse notification = NotificationResponse.builder()
                        .type(NotificationType.ORDER_STATE_CHANGED)
                        .orderId(orderId)
                        .clientId(clientId)
                        .title("Order Update")
                        .message("Order " + orderId + " transitioned to " + newState)
                        .timestamp(Instant.now())
                        .build();

                notificationService.pushToClient(clientId, notification);
                notificationService.broadcast(notification);
            } catch (Exception e) {
                log.warn("OrderStateConsumer: could not parse event payload: {}", e.getMessage());
            }
        }
    }
}