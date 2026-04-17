package com.brokerdealer.riskservice.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RiskResultProducer {

    private static final String RISK_EVALUATION_RESULT = "risk.evaluation.result";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishRiskResult(Object event) {
        // TODO: implement — JIRA story BD-XX
        log.info("Stub: publishRiskResult called");
    }
}
