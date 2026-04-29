package com.brokerdealer.clientservice.kafka.consumer;

import com.brokerdealer.clientservice.kafka.event.ExecutionCompletedEvent;
import com.brokerdealer.clientservice.kafka.event.OrderStateChangedEvent;
import com.brokerdealer.clientservice.service.PortfolioUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final PortfolioUpdateService portfolioUpdateService;

    @KafkaListener(topics = "order.state.changed",
                   groupId = "client-service-order-state-group")
    public void consumeOrderStateChanged(OrderStateChangedEvent event) {
        log.debug("Received order.state.changed: orderId={} side={} newState={}",
                event.getOrderId(), event.getSide(), event.getNewState());
        portfolioUpdateService.handleOrderStateChanged(event);
    }

    @KafkaListener(topics = "execution.completed",
                   groupId = "client-service-execution-group")
    public void consumeExecutionCompleted(ExecutionCompletedEvent event) {
        log.debug("Received execution.completed: executionId={} side={} symbol={} qty={}",
                event.getExecutionId(), event.getSide(), event.getSymbol(), event.getFillQuantity());
        portfolioUpdateService.handleExecutionCompleted(event);
    }
}
