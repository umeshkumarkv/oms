package com.brokerdealer.riskservice.kafka.consumer;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import com.brokerdealer.riskservice.dto.RiskEvaluationResult;
import com.brokerdealer.riskservice.kafka.producer.RiskResultProducer;
import com.brokerdealer.riskservice.service.RiskEvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RiskEvaluationConsumer {

    private final RiskEvaluationService riskEvaluationService;
    private final RiskResultProducer riskResultProducer;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "risk.evaluation.requested",
                   groupId = "risk-service-risk.evaluation.requested-group")
    public void consumeRiskEvaluationRequested(Map<String, Object> event) {
        RiskEvaluationRequest request = objectMapper.convertValue(event, RiskEvaluationRequest.class);
        log.info("Risk evaluation requested: orderId={}", request.getOrderId());

        RiskResult result = riskEvaluationService.evaluate(request);

        riskResultProducer.publishRiskResult(RiskEvaluationResult.builder()
                .orderId(request.getOrderId())
                .clientId(request.getClientId())
                .userId(request.getUserId())
                .result(result.getResult())
                .violatedRuleCode(result.getFailedRule())
                .rejectionReason(result.getReason())
                .timestamp(Instant.now())
                .build());
    }
}
