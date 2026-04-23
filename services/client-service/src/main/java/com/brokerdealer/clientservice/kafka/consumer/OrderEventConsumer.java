package com.brokerdealer.clientservice.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    @KafkaListener(topics = "order.state.changed",
                   groupId = "client-service-order.state.changed-group")
    public void consumeOrderStateChanged(Object event) {
        // TODO: implement — JIRA story BD-XX
        log.info("Stub: OrderEventConsumer.consumeOrderStateChanged received message");
    }

    @KafkaListener(topics = "execution.completed",
                   groupId = "client-service-execution.completed-group")
    public void consumeExecutionCompleted(Object event) {
        // TODO: implement — JIRA story BD-XX
        log.info("Stub: OrderEventConsumer.consumeExecutionCompleted received message");
    }
}
