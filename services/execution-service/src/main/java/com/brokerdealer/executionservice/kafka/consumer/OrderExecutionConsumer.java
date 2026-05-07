package com.brokerdealer.executionservice.kafka.consumer;

import com.brokerdealer.executionservice.dto.request.OrderExecutionRequest;
import com.brokerdealer.executionservice.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExecutionConsumer {

    private final ExecutionService executionService;

    @KafkaListener(
            topics = "order.execution.requested",
            groupId = "execution-service-order.execution.requested-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderExecutionRequested(OrderExecutionRequest event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("Received null or invalid order.execution.requested message — ignoring");
            return;
        }
        log.info("Received order.execution.requested: orderId={} orderType={}",
                event.getOrderId(), event.getOrderType());
        executionService.processFill(event);
    }
}
