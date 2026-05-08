package com.brokerdealer.orderservice.kafka.producer;

import com.brokerdealer.orderservice.dto.kafka.OrderStateChangedEvent;
import com.brokerdealer.orderservice.dto.kafka.RiskEvaluationRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderEventProducer orderEventProducer;

    private final UUID orderId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    @Test
    void shouldPublishOrderStateChangedWithOrderIdAsKafkaKey() {
        OrderStateChangedEvent event = OrderStateChangedEvent.builder()
                .orderId(orderId)
                .clientId(clientId)
                .fromState(null)
                .toState("NEW")
                .triggeredBy("USER:" + UUID.randomUUID())
                .timestamp("2026-05-07T10:00:00Z")
                .metadata(Collections.emptyMap())
                .build();

        orderEventProducer.publishOrderStateChanged(event);

        verify(kafkaTemplate).send("order.state.changed", orderId.toString(), event);
    }

    @Test
    void shouldPublishRiskEvaluationRequestedWithOrderIdAsKafkaKey() {
        RiskEvaluationRequestedEvent event = RiskEvaluationRequestedEvent.builder()
                .orderId(orderId)
                .clientId(clientId)
                .symbol("AAPL")
                .side("BUY")
                .orderType("LIMIT")
                .quantity(100)
                .limitPrice("175.50")
                .timeInForce("DAY")
                .orderValue("17550.00")
                .build();

        orderEventProducer.publishRiskEvaluationRequested(event);

        verify(kafkaTemplate).send("risk.evaluation.requested", orderId.toString(), event);
    }

    @Test
    void shouldIncludeAllFieldsInRiskEvaluationRequestedPayloadForLimitOrder() {
        RiskEvaluationRequestedEvent event = RiskEvaluationRequestedEvent.builder()
                .orderId(orderId)
                .clientId(clientId)
                .symbol("TSLA")
                .side("BUY")
                .orderType("LIMIT")
                .quantity(50)
                .limitPrice("200.00")
                .timeInForce("GTD")
                .orderValue("10000.00")
                .build();

        orderEventProducer.publishRiskEvaluationRequested(event);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
                org.mockito.ArgumentMatchers.eq("risk.evaluation.requested"),
                org.mockito.ArgumentMatchers.eq(orderId.toString()),
                payloadCaptor.capture()
        );

        RiskEvaluationRequestedEvent captured = (RiskEvaluationRequestedEvent) payloadCaptor.getValue();
        assertThat(captured.getOrderValue()).isEqualTo("10000.00");
        assertThat(captured.getLimitPrice()).isEqualTo("200.00");
        assertThat(captured.getTimeInForce()).isEqualTo("GTD");
        assertThat(captured.getSymbol()).isEqualTo("TSLA");
        assertThat(captured.getQuantity()).isEqualTo(50);
    }
}
