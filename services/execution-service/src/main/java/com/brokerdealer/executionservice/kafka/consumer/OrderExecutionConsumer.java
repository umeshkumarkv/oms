package com.brokerdealer.executionservice.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExecutionConsumer {

    @KafkaListener(topics = "order.execution.requested",
                   groupId = "execution-service-order.execution.requested-group")
    public void consumeOrderExecutionRequested(Object event) {
        // TODO: implement — JIRA story BD-XX
        log.info("Stub: OrderExecutionConsumer received message");
    }
}
