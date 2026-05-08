package com.brokerdealer.riskservice.kafka;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskEvaluationResult;
import com.brokerdealer.riskservice.kafka.consumer.RiskEvaluationConsumer;
import com.brokerdealer.riskservice.kafka.producer.RiskResultProducer;
import com.brokerdealer.riskservice.service.RiskEvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskEvaluationConsumerTest {

    @Mock
    private RiskEvaluationService riskEvaluationService;

    @Mock
    private RiskResultProducer riskResultProducer;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RiskEvaluationConsumer riskEvaluationConsumer;

    @Test
    void shouldPublishPassedResultWhenAllRulesPass() {
        UUID orderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        Map<String, Object> event = Map.of(
                "orderId", orderId.toString(),
                "clientId", clientId.toString(),
                "symbol", "AAPL",
                "side", "BUY",
                "orderType", "LIMIT",
                "quantity", 100,
                "limitPrice", "150.00",
                "orderValue", "15000.00"
        );
        when(riskEvaluationService.evaluate(any())).thenReturn(RiskResult.passed());

        riskEvaluationConsumer.consumeRiskEvaluationRequested(event);

        ArgumentCaptor<RiskEvaluationResult> captor = ArgumentCaptor.forClass(RiskEvaluationResult.class);
        verify(riskResultProducer).publishRiskResult(captor.capture());

        RiskEvaluationResult result = captor.getValue();
        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getResult()).isEqualTo("PASSED");
        assertThat(result.getRuleCode()).isNull();
        assertThat(result.getTimestamp()).isNotNull();
    }

    @Test
    void shouldPublishRejectedResultWhenRuleTriggered() {
        UUID orderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        Map<String, Object> event = Map.of(
                "orderId", orderId.toString(),
                "clientId", clientId.toString(),
                "symbol", "AAPL",
                "side", "BUY",
                "orderType", "LIMIT",
                "quantity", 15000,
                "limitPrice", "150.00",
                "orderValue", "2250000.00"
        );
        when(riskEvaluationService.evaluate(any())).thenReturn(
                RiskResult.rejected("SINGLE_ORDER_QTY_LIMIT", "Qty 15000 exceeds limit of 10000"));

        riskEvaluationConsumer.consumeRiskEvaluationRequested(event);

        ArgumentCaptor<RiskEvaluationResult> captor = ArgumentCaptor.forClass(RiskEvaluationResult.class);
        verify(riskResultProducer).publishRiskResult(captor.capture());

        RiskEvaluationResult result = captor.getValue();
        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getRuleCode()).isEqualTo("SINGLE_ORDER_QTY_LIMIT");
        assertThat(result.getReason()).contains("15000");
    }

    @Test
    void shouldPropagateExceptionSoKafkaErrorHandlerCanRouteToDlt() {
        // Consumer must NOT swallow exceptions — Spring Kafka's DefaultErrorHandler
        // catches the propagated exception, retries 3 times, then routes to DLT
        UUID orderId = UUID.randomUUID();
        Map<String, Object> event = Map.of(
                "orderId", orderId.toString(),
                "clientId", UUID.randomUUID().toString(),
                "symbol", "AAPL",
                "side", "BUY",
                "orderType", "LIMIT",
                "quantity", 100,
                "limitPrice", "150.00",
                "orderValue", "15000.00"
        );
        when(riskEvaluationService.evaluate(any())).thenThrow(new RuntimeException("simulated downstream failure"));

        assertThatThrownBy(() -> riskEvaluationConsumer.consumeRiskEvaluationRequested(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated downstream failure");
    }
}
