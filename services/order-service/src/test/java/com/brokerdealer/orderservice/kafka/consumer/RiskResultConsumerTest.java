package com.brokerdealer.orderservice.kafka.consumer;

import com.brokerdealer.orderservice.service.OrderStateMachineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RiskResultConsumerTest {

    @Mock
    private OrderStateMachineService orderStateMachineService;

    private RiskResultConsumer riskResultConsumer;

    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        riskResultConsumer = new RiskResultConsumer(orderStateMachineService, new ObjectMapper());
    }

    @Test
    void shouldInvokeTransitionOnPassedWhenResultIsPassed() {
        Map<String, Object> payload = Map.of(
                "orderId", orderId.toString(),
                "result", "PASSED"
        );

        riskResultConsumer.consumeRiskResult(payload);

        verify(orderStateMachineService).transitionOnRiskPassed(orderId);
        verify(orderStateMachineService, never()).transitionOnRiskRejected(any(), any(), any());
    }

    @Test
    void shouldInvokeTransitionOnRejectedWhenResultIsRejected() {
        Map<String, Object> payload = Map.of(
                "orderId", orderId.toString(),
                "result", "REJECTED",
                "ruleCode", "SINGLE_ORDER_QTY_LIMIT",
                "reason", "Quantity 15000 exceeds limit of 10000"
        );

        riskResultConsumer.consumeRiskResult(payload);

        verify(orderStateMachineService).transitionOnRiskRejected(
                orderId, "SINGLE_ORDER_QTY_LIMIT", "Quantity 15000 exceeds limit of 10000");
        verify(orderStateMachineService, never()).transitionOnRiskPassed(any());
    }

    @Test
    void shouldNotInvokeStateMachineForUnknownResult() {
        Map<String, Object> payload = Map.of(
                "orderId", orderId.toString(),
                "result", "UNKNOWN"
        );

        riskResultConsumer.consumeRiskResult(payload);

        verify(orderStateMachineService, never()).transitionOnRiskPassed(any());
        verify(orderStateMachineService, never()).transitionOnRiskRejected(any(), any(), any());
    }
}
