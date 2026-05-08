package com.brokerdealer.notificationservice.kafka.consumer;

import com.brokerdealer.notificationservice.domain.NotificationType;
import com.brokerdealer.notificationservice.dto.event.OrderStateChangedEvent;
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
public class OrderStateConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "order.state.changed",
            groupId = "notification-service-order.state.changed-group",
            containerFactory = "orderStateListenerContainerFactory"
    )
    public void consumeOrderStateChanged(OrderStateChangedEvent event) {
        if (event.getClientId() == null || event.getOrderId() == null) {
            log.warn("OrderStateConsumer: skipping event with null clientId or orderId");
            return;
        }

        log.info("OrderStateConsumer: orderId={} {} -> {}",
                event.getOrderId(), event.getPreviousState(), event.getNewState());

        NotificationResponse notification = NotificationResponse.builder()
                .type(NotificationType.ORDER_STATE_CHANGED)
                .orderId(event.getOrderId())
                .clientId(event.getClientId())
                .title("Order Update")
                .message("Order " + event.getOrderId() + " transitioned "
                        + event.getPreviousState() + " \u2192 " + event.getNewState())
                .timestamp(Instant.now())
                .build();

        notificationService.pushToClient(event.getClientId(), notification);
        notificationService.broadcast(notification);
    }
}
