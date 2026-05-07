package com.brokerdealer.orderservice.kafka.consumer;

import com.brokerdealer.orderservice.dto.kafka.RiskEvaluationResultEvent;
import com.brokerdealer.orderservice.service.OrderStateMachineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RiskResultConsumer {

    private final OrderStateMachineService orderStateMachineService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "risk.evaluation.result",
            groupId = "order-service-risk.evaluation.result-group"
    )
    public void consumeRiskResult(Map<String, Object> payload) {
        RiskEvaluationResultEvent event = objectMapper.convertValue(payload, RiskEvaluationResultEvent.class);
        UUID orderId = event.getOrderId();
        log.info("Risk result consumed: orderId={} result={}", orderId, event.getResult());

        if ("PASSED".equals(event.getResult())) {
            orderStateMachineService.transitionOnRiskPassed(orderId);
        } else if ("REJECTED".equals(event.getResult())) {
            orderStateMachineService.transitionOnRiskRejected(orderId, event.getRuleCode(), event.getReason());
        } else {
            log.warn("Unknown risk result received: orderId={} result={}", orderId, event.getResult());
        }
    }

    @KafkaListener(
            topics = "execution.completed",
            groupId = "order-service-execution.completed-group"
    )
    public void consumeExecutionCompleted(Map<String, Object> payload) {
        // TODO: implement — SCRUM-48
        log.info("Stub: consumeExecutionCompleted received message");
    }
}
