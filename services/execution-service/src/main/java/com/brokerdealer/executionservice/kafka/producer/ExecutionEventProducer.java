package com.brokerdealer.executionservice.kafka.producer;

import com.brokerdealer.executionservice.dto.event.ExecutionCompletedEvent;
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

    public void publishExecutionCompleted(ExecutionCompletedEvent event) {
        kafkaTemplate.send(EXECUTION_COMPLETED, event.getOrderId().toString(), event);
        log.info("Published execution.completed: orderId={} executionId={} fillType={}",
                event.getOrderId(), event.getExecutionId(), event.getFillType());
    }
}
