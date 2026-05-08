package com.brokerdealer.notificationservice.kafka.consumer;

import com.brokerdealer.notificationservice.domain.NotificationType;
import com.brokerdealer.notificationservice.dto.event.RiskEvaluationResultEvent;
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
public class RiskResultConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "risk.evaluation.result",
            groupId = "notification-service-risk.evaluation.result-group",
            containerFactory = "riskResultListenerContainerFactory"
    )
    public void consumeRiskResult(RiskEvaluationResultEvent event) {
        if (event.getClientId() == null || event.getOrderId() == null) {
            log.warn("RiskResultConsumer: skipping event with null clientId or orderId");
            return;
        }

        if ("PASSED".equals(event.getResult())) {
            log.debug("RiskResultConsumer: orderId={} PASSED — no push", event.getOrderId());
            return;
        }

        log.info("RiskResultConsumer: orderId={} REJECTED rule={}", event.getOrderId(), event.getFailedRule());

        NotificationResponse notification = NotificationResponse.builder()
                .type(NotificationType.RISK_RESULT)
                .orderId(event.getOrderId())
                .clientId(event.getClientId())
                .title("Order Rejected")
                .message("Order " + event.getOrderId() + " rejected by risk rule "
                        + event.getFailedRule() + ": " + event.getReason())
                .timestamp(Instant.now())
                .build();

        notificationService.pushToClient(event.getClientId(), notification);
        notificationService.broadcast(notification);
    }
}
