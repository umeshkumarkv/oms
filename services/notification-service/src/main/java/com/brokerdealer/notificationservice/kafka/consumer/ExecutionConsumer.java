package com.brokerdealer.notificationservice.kafka.consumer;

import com.brokerdealer.notificationservice.domain.NotificationType;
import com.brokerdealer.notificationservice.dto.event.ExecutionCompletedEvent;
import com.brokerdealer.notificationservice.dto.response.NotificationResponse;
import com.brokerdealer.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "execution.completed",
            groupId = "notification-service-execution.completed-group",
            containerFactory = "executionListenerContainerFactory"
    )
    public void consumeExecutionCompleted(ExecutionCompletedEvent event) {
        if (event.getClientId() == null || event.getOrderId() == null) {
            log.warn("ExecutionConsumer: skipping event with null clientId or orderId");
            return;
        }

        log.info("ExecutionConsumer: orderId={} fillType={}", event.getOrderId(), event.getFillType());

        String title;
        String message;

        if ("FULL".equals(event.getFillType())) {
            title = "Order Filled";
            message = event.getSymbol() + " " + event.getSide() + " " + event.getExecutedQty()
                    + " \u2014 Fully filled at " + event.getExecutedPrice();
        } else {
            title = "Partial Fill";
            message = event.getSymbol() + " " + event.getSide() + " " + event.getExecutedQty()
                    + " filled at " + event.getExecutedPrice()
                    + " \u2014 " + event.getRemainingQuantity() + " remaining";
        }

        NotificationResponse notification = NotificationResponse.builder()
                .type(NotificationType.EXECUTION_COMPLETED)
                .orderId(event.getOrderId())
                .clientId(event.getClientId())
                .title(title)
                .message(message)
                .timestamp(Instant.now())
                .build();

        notificationService.pushToClient(event.getClientId(), notification);
        notificationService.broadcast(notification);
    }
}
