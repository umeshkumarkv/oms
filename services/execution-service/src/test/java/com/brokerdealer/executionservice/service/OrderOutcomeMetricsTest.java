package com.brokerdealer.executionservice.service;

import com.brokerdealer.executionservice.domain.*;
import com.brokerdealer.executionservice.dto.request.OrderExecutionRequest;
import com.brokerdealer.executionservice.dto.response.MarketDataResponse;
import com.brokerdealer.executionservice.kafka.producer.ExecutionEventProducer;
import com.brokerdealer.executionservice.metrics.OrderMetricsService;
import com.brokerdealer.executionservice.repository.ExecutionRepository;
import com.brokerdealer.executionservice.repository.PendingOrderExecutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderOutcomeMetricsTest {

    @Mock private ExecutionRepository executionRepository;
    @Mock private PendingOrderExecutionRepository pendingRepository;
    @Mock private ExecutionEventProducer executionEventProducer;
    @Mock private MarketDataClient marketDataClient;
    @Mock private OrderMetricsService orderMetricsService;

    @InjectMocks
    private FillSimulationService fillSimulationService;

    private static final UUID ORDER_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString("b2000000-0000-0000-0000-000000000001");

    @Test
    void shouldRecordMetricsOnFullFill() {
        OrderExecutionRequest request = buildRequest(OrderType.MARKET, OrderSide.BUY,
                5, new BigDecimal("175.50"), null);
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeMarketFill(request);

        verify(orderMetricsService).recordOrderOutcome("FULL");
    }

    @Test
    void shouldRecordMetricsOnRejectedFill() {
        OrderExecutionRequest request = buildRequest(OrderType.MARKET, OrderSide.BUY,
                1001, new BigDecimal("175.50"), null);
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeMarketFill(request);

        verify(orderMetricsService).recordOrderOutcome("REJECTED");
    }

    private OrderExecutionRequest buildRequest(OrderType orderType, OrderSide side,
                                               int qty, BigDecimal lastPrice, BigDecimal limitPrice) {
        OrderExecutionRequest req = new OrderExecutionRequest();
        req.setOrderId(ORDER_ID);
        req.setClientId(CLIENT_ID);
        req.setSymbol("AAPL");
        req.setSide(side);
        req.setOrderType(orderType);
        req.setQuantity(qty);
        req.setLimitPrice(limitPrice);
        req.setOrderValue(lastPrice.multiply(BigDecimal.valueOf(qty)));
        req.setLastPrice(lastPrice);
        return req;
    }

    private com.brokerdealer.executionservice.domain.Execution withId(com.brokerdealer.executionservice.domain.Execution e) {
        return com.brokerdealer.executionservice.domain.Execution.builder()
                .id(UUID.randomUUID()).orderId(e.getOrderId()).clientId(e.getClientId())
                .symbol(e.getSymbol()).side(e.getSide()).executedQty(e.getExecutedQty())
                .executedPrice(e.getExecutedPrice()).fillType(e.getFillType())
                .fillValue(e.getFillValue()).remainingQuantity(e.getRemainingQuantity())
                .marketPriceAtFill(e.getMarketPriceAtFill()).executionVenue(e.getExecutionVenue())
                .executedAt(Instant.now()).build();
    }
}
