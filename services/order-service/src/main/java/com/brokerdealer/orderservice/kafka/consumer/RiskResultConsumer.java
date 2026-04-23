package com.brokerdealer.orderservice.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RiskResultConsumer {

    @KafkaListener(topics = "risk.evaluation.result",
                   groupId = "order-service-risk.evaluation.result-group")
    public void consumeRiskResult(Object event) {
        // TODO: implement — JIRA story BD-XX
        log.info("Stub: RiskResultConsumer received message");
    }

    @KafkaListener(topics = "execution.completed",
                   groupId = "order-service-execution.completed-group")
    public void consumeExecutionCompleted(Object event) {
        // TODO: implement — JIRA story BD-XX
        log.info("Stub: RiskResultConsumer.consumeExecutionCompleted received message");
    }
}
