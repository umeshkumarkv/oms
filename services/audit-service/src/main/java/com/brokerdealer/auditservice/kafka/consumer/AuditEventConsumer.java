package com.brokerdealer.auditservice.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumer {

    @KafkaListener(topics = "order.state.changed",
                   groupId = "audit-service-order.state.changed-group")
    public void consumeOrderStateChanged(Object event) {
        // TODO: implement — JIRA story BD-XX
        log.info("Stub: AuditEventConsumer.consumeOrderStateChanged received message");
    }

    @KafkaListener(topics = "execution.completed",
                   groupId = "audit-service-execution.completed-group")
    public void consumeExecutionCompleted(Object event) {
        // TODO: implement — JIRA story BD-XX
        log.info("Stub: AuditEventConsumer.consumeExecutionCompleted received message");
    }
}
