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
import java.util.UUID;

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
        if (event.getOrderId() == null) {
            log.warn("OrderStateConsumer: skipping event with null orderId");
            return;
        }

        log.info("OrderStateConsumer: orderId={} {} -> {}",
                event.getOrderId(), event.getFromState(), event.getToState());

        NotificationResponse notification = NotificationResponse.builder()
                .type(NotificationType.ORDER_STATE_CHANGED)
                .orderId(event.getOrderId())
                .clientId(event.getClientId())
                .title("Order Update")
                .message("Order " + event.getOrderId() + " transitioned "
                        + event.getFromState() + " \u2192 " + event.getToState())
                .timestamp(Instant.now())
                .build();

        // Push to the trader who owns the order (by auth userId), fallback to clientId
        UUID pushTarget = event.getUserId() != null ? event.getUserId() : event.getClientId();
        if (pushTarget != null) {
            notificationService.pushToClient(pushTarget, notification);
        }
        notificationService.broadcast(notification);
    }
}
