package com.brokerdealer.riskservice.kafka.producer;

import com.brokerdealer.riskservice.dto.RiskEvaluationResult;
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

    public void publishRiskResult(RiskEvaluationResult result) {
        kafkaTemplate.send(RISK_EVALUATION_RESULT, result.getOrderId().toString(), result);
        log.info("Risk result published: orderId={} result={}", result.getOrderId(), result.getResult());
    }
}
