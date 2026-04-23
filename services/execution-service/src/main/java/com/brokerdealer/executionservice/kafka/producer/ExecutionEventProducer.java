package com.brokerdealer.executionservice.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionEventProducer {

    private static final String EXECUTION_COMPLETED = "execution.completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishExecutionCompleted(Object event) {
        // TODO: implement — JIRA story BD-XX
        log.info("Stub: publishExecutionCompleted called");
    }
}
