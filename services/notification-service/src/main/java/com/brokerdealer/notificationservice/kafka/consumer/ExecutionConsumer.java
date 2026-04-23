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
public class ExecutionConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "execution.completed",
                   groupId = "notification-service-execution.completed-group")
    public void consumeExecutionCompleted(Object event) {
        // TODO: implement — JIRA story BD-XX
        // Expected payload fields: executionId, orderId, clientId, symbol, side, quantity, price, status, timestamp
        log.info("Stub: ExecutionConsumer.consumeExecutionCompleted received message");

        // Skeleton push — replace with real event deserialization in implementation story
        if (event instanceof Map<?, ?> payload) {
            try {
                UUID clientId = UUID.fromString(String.valueOf(payload.get("clientId")));
                UUID orderId  = UUID.fromString(String.valueOf(payload.get("orderId")));
                String status = String.valueOf(payload.get("status"));

                NotificationResponse notification = NotificationResponse.builder()
                        .type(NotificationType.EXECUTION_COMPLETED)
                        .orderId(orderId)
                        .clientId(clientId)
                        .title("Execution Update")
                        .message("Order " + orderId + " execution status: " + status)
                        .timestamp(Instant.now())
                        .build();

                notificationService.pushToClient(clientId, notification);
                notificationService.broadcast(notification);
            } catch (Exception e) {
                log.warn("ExecutionConsumer: could not parse event payload: {}", e.getMessage());
            }
        }
    }
}