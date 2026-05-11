package com.brokerdealer.orderservice.kafka.producer;

import com.brokerdealer.orderservice.dto.kafka.OrderExecutionRequestedEvent;
import com.brokerdealer.orderservice.dto.kafka.OrderStateChangedEvent;
import com.brokerdealer.orderservice.dto.kafka.RiskEvaluationRequestedEvent;
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

    public void publishOrderStateChanged(OrderStateChangedEvent event) {
        kafkaTemplate.send(ORDER_STATE_CHANGED, event.getOrderId().toString(), event);
        log.debug("Published order.state.changed: orderId={} {} -> {}",
                event.getOrderId(), event.getFromState(), event.getToState());
    }

    public void publishRiskEvaluationRequested(RiskEvaluationRequestedEvent event) {
        kafkaTemplate.send(RISK_EVALUATION_REQUESTED, event.getOrderId().toString(), event);
        log.debug("Published risk.evaluation.requested: orderId={} symbol={} orderValue={}",
                event.getOrderId(), event.getSymbol(), event.getOrderValue());
    }

    public void publishOrderExecutionRequested(OrderExecutionRequestedEvent event) {
        kafkaTemplate.send(ORDER_EXECUTION_REQUESTED, event.getOrderId().toString(), event);
        log.debug("Published order.execution.requested: orderId={} symbol={} orderType={}",
                event.getOrderId(), event.getSymbol(), event.getOrderType());
    }
}
