package com.brokerdealer.riskservice.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RiskEvaluationConsumer {

    @KafkaListener(topics = "risk.evaluation.requested",
                   groupId = "risk-service-risk.evaluation.requested-group")
    public void consumeRiskEvaluationRequested(Object event) {
        // TODO: implement — JIRA story BD-XX
        log.info("Stub: RiskEvaluationConsumer received message");
    }
}
