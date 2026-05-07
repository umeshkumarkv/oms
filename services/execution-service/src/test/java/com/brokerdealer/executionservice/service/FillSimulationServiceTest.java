package com.brokerdealer.executionservice.service;

import com.brokerdealer.executionservice.domain.*;
import com.brokerdealer.executionservice.dto.event.ExecutionCompletedEvent;
import com.brokerdealer.executionservice.dto.request.OrderExecutionRequest;
import com.brokerdealer.executionservice.dto.response.MarketDataResponse;
import com.brokerdealer.executionservice.kafka.producer.ExecutionEventProducer;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FillSimulationServiceTest {

    @Mock private ExecutionRepository executionRepository;
    @Mock private PendingOrderExecutionRepository pendingRepository;
    @Mock private ExecutionEventProducer executionEventProducer;
    @Mock private MarketDataClient marketDataClient;

    @InjectMocks
    private FillSimulationService fillSimulationService;

    private static final UUID ORDER_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString("b2000000-0000-0000-0000-000000000001");

    // ── MARKET fill ───────────────────────────────────────────────────────────

    @Test
    void shouldFillMarketOrderImmediatelyAtLastPrice() {
        OrderExecutionRequest request = buildRequest(OrderType.MARKET, OrderSide.BUY,
                100, new BigDecimal("175.50"), null);
        when(executionRepository.save(any())).thenAnswer(inv -> {
            Execution e = inv.getArgument(0);
            e = Execution.builder()
                    .id(UUID.randomUUID()).orderId(e.getOrderId()).clientId(e.getClientId())
                    .symbol(e.getSymbol()).side(e.getSide()).executedQty(e.getExecutedQty())
                    .executedPrice(e.getExecutedPrice()).fillType(e.getFillType())
                    .fillValue(e.getFillValue()).remainingQuantity(e.getRemainingQuantity())
                    .marketPriceAtFill(e.getMarketPriceAtFill()).executionVenue(e.getExecutionVenue())
                    .executedAt(Instant.now()).build();
            return e;
        });

        fillSimulationService.executeMarketFill(request);

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository).save(captor.capture());
        Execution saved = captor.getValue();

        assertThat(saved.getFillType()).isEqualTo(FillType.FULL);
        assertThat(saved.getExecutedPrice()).isEqualByComparingTo(new BigDecimal("175.50"));
        assertThat(saved.getRemainingQuantity()).isZero();
        assertThat(saved.getExecutionVenue()).isEqualTo("MOCK_ECN");
        verify(executionEventProducer).publishExecutionCompleted(any(ExecutionCompletedEvent.class));
    }

    @Test
    void shouldReturnCorrectFillValueForMarketOrder() {
        OrderExecutionRequest request = buildRequest(OrderType.MARKET, OrderSide.BUY,
                100, new BigDecimal("175.50"), null);
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeMarketFill(request);

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository).save(captor.capture());
        // fillValue = 100 × 175.50 = 17550.00
        assertThat(captor.getValue().getFillValue()).isEqualByComparingTo(new BigDecimal("17550.00"));
    }

    // ── LIMIT condition check ──────────────────────────────────────────────────

    @Test
    void shouldReturnTrueWhenBuyLimitConditionMet() {
        boolean result = fillSimulationService.checkLimitCondition(
                OrderSide.BUY, new BigDecimal("174.00"), new BigDecimal("175.00"));
        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenBuyLimitConditionNotMet() {
        boolean result = fillSimulationService.checkLimitCondition(
                OrderSide.BUY, new BigDecimal("176.00"), new BigDecimal("175.00"));
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnTrueWhenSellLimitConditionMet() {
        boolean result = fillSimulationService.checkLimitCondition(
                OrderSide.SELL, new BigDecimal("176.00"), new BigDecimal("175.00"));
        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenSellLimitConditionNotMet() {
        boolean result = fillSimulationService.checkLimitCondition(
                OrderSide.SELL, new BigDecimal("174.00"), new BigDecimal("175.00"));
        assertThat(result).isFalse();
    }

    // ── LIMIT full fill ───────────────────────────────────────────────────────

    @Test
    void shouldFillLimitOrderAtLimitPriceNotMarketPrice() {
        OrderExecutionRequest request = buildRequest(OrderType.LIMIT, OrderSide.BUY,
                100, new BigDecimal("175.50"), new BigDecimal("176.00"));
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeFullLimitFill(request, new BigDecimal("175.50"));

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository).save(captor.capture());
        // executedPrice must be limitPrice (176.00), not marketPrice
        assertThat(captor.getValue().getExecutedPrice()).isEqualByComparingTo(new BigDecimal("176.00"));
        assertThat(captor.getValue().getFillType()).isEqualTo(FillType.FULL);
        assertThat(captor.getValue().getRemainingQuantity()).isZero();
    }

    // ── LIMIT partial fill ────────────────────────────────────────────────────

    @Test
    void shouldSetRemainingQuantityCorrectlyForPartialFillTranche1() {
        OrderExecutionRequest request = buildRequest(OrderType.LIMIT, OrderSide.BUY,
                100, new BigDecimal("175.50"), new BigDecimal("176.00"));
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executePartialLimitFill(request, new BigDecimal("175.50"));

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository, atLeast(1)).save(captor.capture());
        // First call is tranche1: 60% = 60 qty, remaining = 40
        Execution tranche1 = captor.getAllValues().get(0);
        assertThat(tranche1.getFillType()).isEqualTo(FillType.PARTIAL);
        assertThat(tranche1.getExecutedQty()).isEqualTo(60);
        assertThat(tranche1.getRemainingQuantity()).isEqualTo(40);
    }

    // ── Queue pending LIMIT order ─────────────────────────────────────────────

    @Test
    void shouldQueueLimitOrderWhenPriceConditionNotMet() {
        // BUY limit 175.00, market price 176.00 → condition NOT met
        OrderExecutionRequest request = buildRequest(OrderType.LIMIT, OrderSide.BUY,
                100, new BigDecimal("176.00"), new BigDecimal("175.00"));

        fillSimulationService.initiateLimitFill(request);

        ArgumentCaptor<PendingOrderExecution> captor = ArgumentCaptor.forClass(PendingOrderExecution.class);
        verify(pendingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PendingStatus.PENDING);
        assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
        verifyNoInteractions(executionRepository);
    }

    // ── @Scheduled monitor ────────────────────────────────────────────────────

    @Test
    void shouldSkipOrderInSchedulerWhenMarketDataCallFails() {
        PendingOrderExecution pending = buildPending();
        when(pendingRepository.findByStatus(PendingStatus.PENDING)).thenReturn(List.of(pending));
        when(marketDataClient.getMarketData("AAPL")).thenReturn(Optional.empty());

        fillSimulationService.monitorPendingLimitOrders();

        verifyNoInteractions(executionRepository);
        verifyNoInteractions(executionEventProducer);
    }

    @Test
    void shouldMarkPendingOrderAsProcessedAfterFillInScheduler() {
        PendingOrderExecution pending = buildPending();
        MarketDataResponse marketData = new MarketDataResponse();
        // BUY limit=175.00, market=174.00 → condition MET
        marketData.setLastPrice(new BigDecimal("174.00"));
        when(pendingRepository.findByStatus(PendingStatus.PENDING)).thenReturn(List.of(pending));
        when(marketDataClient.getMarketData("AAPL")).thenReturn(Optional.of(marketData));
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.monitorPendingLimitOrders();

        ArgumentCaptor<PendingOrderExecution> captor = ArgumentCaptor.forClass(PendingOrderExecution.class);
        verify(pendingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PendingStatus.PROCESSED);
    }

    @Test
    void shouldDoNothingInSchedulerWhenNoPendingOrders() {
        when(pendingRepository.findByStatus(PendingStatus.PENDING)).thenReturn(List.of());

        fillSimulationService.monitorPendingLimitOrders();

        verifyNoInteractions(executionRepository);
        verifyNoInteractions(marketDataClient);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private PendingOrderExecution buildPending() {
        return PendingOrderExecution.builder()
                .id(UUID.randomUUID())
                .orderId(ORDER_ID)
                .clientId(CLIENT_ID)
                .symbol("AAPL")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .quantity(100)
                .limitPrice(new BigDecimal("175.00"))
                .lastPrice(new BigDecimal("176.00"))
                .orderValue(new BigDecimal("17600.00"))
                .status(PendingStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Execution withId(Execution e) {
        return Execution.builder()
                .id(UUID.randomUUID()).orderId(e.getOrderId()).clientId(e.getClientId())
                .symbol(e.getSymbol()).side(e.getSide()).executedQty(e.getExecutedQty())
                .executedPrice(e.getExecutedPrice()).fillType(e.getFillType())
                .fillValue(e.getFillValue()).remainingQuantity(e.getRemainingQuantity())
                .marketPriceAtFill(e.getMarketPriceAtFill()).executionVenue(e.getExecutionVenue())
                .executedAt(Instant.now()).build();
    }
}
