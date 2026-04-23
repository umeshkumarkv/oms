package com.brokerdealer.orderservice.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private static final String ORDER_STATE_CHANGED = "order.state.changed";
    private static final String RISK_EVALUATION_REQUESTED = "risk.evaluation.requested";
    private static final String ORDER_EXECUTION_REQUESTED = "order.execution.requested";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderStateChanged(Object event) {
        // TODO: implement — JIRA story BD-XX
        log.info("Stub: publishOrderStateChanged called");
    }

    public void publishRiskEvaluationRequested(Object event) {
        // TODO: implement — JIRA story BD-XX
        log.info("Stub: publishRiskEvaluationRequested called");
    }

    public void publishOrderExecutionRequested(Object event) {
        // TODO: implement — JIRA story BD-XX
        log.info("Stub: publishOrderExecutionRequested called");
    }
}
