package com.brokerdealer.executionservice.service;

import com.brokerdealer.executionservice.domain.*;
import com.brokerdealer.executionservice.dto.event.ExecutionCompletedEvent;
import com.brokerdealer.executionservice.dto.request.OrderExecutionRequest;
import com.brokerdealer.executionservice.dto.response.MarketDataResponse;
import com.brokerdealer.executionservice.kafka.producer.ExecutionEventProducer;
import com.brokerdealer.executionservice.repository.ExecutionRepository;
import com.brokerdealer.executionservice.repository.PendingOrderExecutionRepository;
import com.brokerdealer.executionservice.metrics.OrderMetricsService;
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
    @Mock private OrderMetricsService orderMetricsService;

    @InjectMocks
    private FillSimulationService fillSimulationService;

    private static final UUID ORDER_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString("b2000000-0000-0000-0000-000000000001");

    // ── determineFillOutcome — quantity ranges ─────────────────────────────────

    @Test
    void shouldReturnFullWhenQuantityIsLessThan10() {
        OrderExecutionRequest req = buildRequest(OrderType.MARKET, OrderSide.BUY, 5, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.FULL);
    }

    @Test
    void shouldReturnFullWhenQuantityIsOne() {
        OrderExecutionRequest req = buildRequest(OrderType.MARKET, OrderSide.BUY, 1, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.FULL);
    }

    @Test
    void shouldReturnPartialWhenQuantityIs10() {
        OrderExecutionRequest req = buildRequest(OrderType.MARKET, OrderSide.BUY, 10, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.PARTIAL);
    }

    @Test
    void shouldReturnPartialWhenQuantityIs50() {
        OrderExecutionRequest req = buildRequest(OrderType.MARKET, OrderSide.BUY, 50, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.PARTIAL);
    }

    @Test
    void shouldReturnPartialWhenQuantityIs99() {
        OrderExecutionRequest req = buildRequest(OrderType.MARKET, OrderSide.BUY, 99, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.PARTIAL);
    }

    @Test
    void shouldReturnFullWhenQuantityIs100() {
        OrderExecutionRequest req = buildRequest(OrderType.MARKET, OrderSide.BUY, 100, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.FULL);
    }

    @Test
    void shouldReturnFullWhenQuantityIs500() {
        OrderExecutionRequest req = buildRequest(OrderType.MARKET, OrderSide.BUY, 500, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.FULL);
    }

    @Test
    void shouldReturnFullWhenQuantityIs999() {
        OrderExecutionRequest req = buildRequest(OrderType.MARKET, OrderSide.BUY, 999, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.FULL);
    }

    @Test
    void shouldReturnRejectedWhenQuantityIs1000() {
        OrderExecutionRequest req = buildRequest(OrderType.MARKET, OrderSide.BUY, 1000, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.REJECTED);
    }

    @Test
    void shouldReturnRejectedWhenQuantityIs1001() {
        OrderExecutionRequest req = buildRequest(OrderType.MARKET, OrderSide.BUY, 1001, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.REJECTED);
    }

    @Test
    void shouldReturnRejectedWhenQuantityIs1003() {
        OrderExecutionRequest req = buildRequest(OrderType.MARKET, OrderSide.BUY, 1003, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.REJECTED);
    }

    @Test
    void shouldReturnFullWhenQuantityIs1004() {
        OrderExecutionRequest req = buildRequest(OrderType.MARKET, OrderSide.BUY, 1004, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.FULL);
    }

    @Test
    void shouldReturnFullWhenQuantityIs2000() {
        OrderExecutionRequest req = buildRequest(OrderType.MARKET, OrderSide.BUY, 2000, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.FULL);
    }

    // ── determineFillOutcome — symbol overrides ────────────────────────────────

    @Test
    void shouldReturnRejectedWhenSymbolIsFail() {
        OrderExecutionRequest req = buildRequestWithSymbol("FAIL", 5, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.REJECTED);
    }

    @Test
    void shouldReturnRejectedWhenSymbolIsFailRegardlessOfQty() {
        OrderExecutionRequest req = buildRequestWithSymbol("FAIL", 500, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.REJECTED);
    }

    @Test
    void shouldReturnPartialWhenSymbolIsSlow() {
        OrderExecutionRequest req = buildRequestWithSymbol("SLOW", 5, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.PARTIAL);
    }

    @Test
    void shouldReturnPartialWhenSymbolIsSlowOverridesRejectRange() {
        OrderExecutionRequest req = buildRequestWithSymbol("SLOW", 1001, new BigDecimal("175.50"), null);
        assertThat(fillSimulationService.determineFillOutcome(req)).isEqualTo(FillType.PARTIAL);
    }

    // ── executeMarketFill — deterministic routing ──────────────────────────────

    @Test
    void shouldFullFillMarketOrderWhenQtyLessThan10() {
        OrderExecutionRequest request = buildRequest(OrderType.MARKET, OrderSide.BUY,
                5, new BigDecimal("175.50"), null);
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeMarketFill(request);

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository).save(captor.capture());
        assertThat(captor.getValue().getFillType()).isEqualTo(FillType.FULL);
        assertThat(captor.getValue().getExecutedQty()).isEqualTo(5);
        assertThat(captor.getValue().getRemainingQuantity()).isZero();
        verify(executionEventProducer).publishExecutionCompleted(any(ExecutionCompletedEvent.class));
    }

    @Test
    void shouldPartialFillMarketOrderWhenQtyBetween10And99() {
        OrderExecutionRequest request = buildRequest(OrderType.MARKET, OrderSide.BUY,
                50, new BigDecimal("175.50"), null);
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeMarketFill(request);

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository, atLeast(1)).save(captor.capture());
        Execution tranche1 = captor.getAllValues().get(0);
        assertThat(tranche1.getFillType()).isEqualTo(FillType.PARTIAL);
        assertThat(tranche1.getExecutedQty()).isEqualTo(30); // floor(50 * 0.60)
        assertThat(tranche1.getRemainingQuantity()).isEqualTo(20);
    }

    @Test
    void shouldRejectMarketOrderWhenQtyBetween1000And1003() {
        OrderExecutionRequest request = buildRequest(OrderType.MARKET, OrderSide.BUY,
                1001, new BigDecimal("175.50"), null);
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeMarketFill(request);

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository).save(captor.capture());
        assertThat(captor.getValue().getFillType()).isEqualTo(FillType.REJECTED);
        assertThat(captor.getValue().getExecutedQty()).isZero();
        assertThat(captor.getValue().getFillValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(captor.getValue().getRemainingQuantity()).isEqualTo(1001);
        verify(executionEventProducer).publishExecutionCompleted(any(ExecutionCompletedEvent.class));
    }

    @Test
    void shouldFullFillMarketOrderWhenQtyBetween100And999() {
        OrderExecutionRequest request = buildRequest(OrderType.MARKET, OrderSide.BUY,
                500, new BigDecimal("175.50"), null);
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeMarketFill(request);

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository).save(captor.capture());
        assertThat(captor.getValue().getFillType()).isEqualTo(FillType.FULL);
        assertThat(captor.getValue().getExecutedQty()).isEqualTo(500);
    }

    @Test
    void shouldFullFillMarketOrderWhenQtyAbove1003() {
        OrderExecutionRequest request = buildRequest(OrderType.MARKET, OrderSide.BUY,
                2000, new BigDecimal("175.50"), null);
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeMarketFill(request);

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository).save(captor.capture());
        assertThat(captor.getValue().getFillType()).isEqualTo(FillType.FULL);
        assertThat(captor.getValue().getExecutedQty()).isEqualTo(2000);
    }

    @Test
    void shouldRejectMarketOrderWhenSymbolIsFail() {
        OrderExecutionRequest request = buildRequestWithSymbol("FAIL", 5, new BigDecimal("175.50"), null);
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeMarketFill(request);

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository).save(captor.capture());
        assertThat(captor.getValue().getFillType()).isEqualTo(FillType.REJECTED);
        assertThat(captor.getValue().getExecutedQty()).isZero();
    }

    @Test
    void shouldPartialFillMarketOrderWhenSymbolIsSlowRegardlessOfQty() {
        OrderExecutionRequest request = buildRequestWithSymbol("SLOW", 1001, new BigDecimal("175.50"), null);
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeMarketFill(request);

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository, atLeast(1)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getFillType()).isEqualTo(FillType.PARTIAL);
    }

    // ── executeRejectedFill ────────────────────────────────────────────────────

    @Test
    void shouldPersistExecutionWithZeroQtyOnRejectedFill() {
        OrderExecutionRequest request = buildRequest(OrderType.MARKET, OrderSide.BUY,
                1001, new BigDecimal("175.50"), null);
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeRejectedFill(request, new BigDecimal("175.50"));

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository).save(captor.capture());
        assertThat(captor.getValue().getExecutedQty()).isZero();
        assertThat(captor.getValue().getFillValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldPublishExecutionCompletedWithRejectedFillType() {
        OrderExecutionRequest request = buildRequest(OrderType.MARKET, OrderSide.BUY,
                1001, new BigDecimal("175.50"), null);
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeRejectedFill(request, new BigDecimal("175.50"));

        ArgumentCaptor<ExecutionCompletedEvent> captor = ArgumentCaptor.forClass(ExecutionCompletedEvent.class);
        verify(executionEventProducer).publishExecutionCompleted(captor.capture());
        assertThat(captor.getValue().getFillType()).isEqualTo("REJECTED");
        assertThat(captor.getValue().getExecutedQty()).isZero();
    }

    @Test
    void shouldSetRemainingQuantityToOriginalOrderQuantityOnRejection() {
        OrderExecutionRequest request = buildRequest(OrderType.MARKET, OrderSide.BUY,
                1001, new BigDecimal("175.50"), null);
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeRejectedFill(request, new BigDecimal("175.50"));

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository).save(captor.capture());
        assertThat(captor.getValue().getRemainingQuantity()).isEqualTo(1001);
    }

    // ── executeMarketFill — existing fill-value test ───────────────────────────

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

    // ── executeFill (LIMIT full-fill path) ────────────────────────────────────

    @Test
    void shouldFillLimitOrderAtLimitPriceNotMarketPrice() {
        // qty=100 → FULL, executedPrice must be limitPrice (176.00) not marketPrice (175.50)
        OrderExecutionRequest request = buildRequest(OrderType.LIMIT, OrderSide.BUY,
                100, new BigDecimal("175.50"), new BigDecimal("176.00"));
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeFill(request, request.getLimitPrice(), new BigDecimal("175.50"));

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository).save(captor.capture());
        assertThat(captor.getValue().getExecutedPrice()).isEqualByComparingTo(new BigDecimal("176.00"));
        assertThat(captor.getValue().getFillType()).isEqualTo(FillType.FULL);
        assertThat(captor.getValue().getRemainingQuantity()).isZero();
    }

    // ── executePartialFill (LIMIT partial-fill path) ──────────────────────────

    @Test
    void shouldSetRemainingQuantityCorrectlyForPartialFillTranche1() {
        OrderExecutionRequest request = buildRequest(OrderType.LIMIT, OrderSide.BUY,
                100, new BigDecimal("175.50"), new BigDecimal("176.00"));
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executePartialFill(request, request.getLimitPrice(), new BigDecimal("175.50"));

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository, atLeast(1)).save(captor.capture());
        // tranche1: 60% = 60 qty, remaining = 40
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

    @Test
    void shouldConvertPartialToRejectedWhenFillOrKillTrue() {
        OrderExecutionRequest request = buildRequest(OrderType.MARKET, OrderSide.BUY,
                50, new BigDecimal("175.50"), null);
        request.setFillOrKill(Boolean.TRUE);
        when(executionRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));

        fillSimulationService.executeMarketFill(request);

        ArgumentCaptor<Execution> captor = ArgumentCaptor.forClass(Execution.class);
        verify(executionRepository).save(captor.capture());
        assertThat(captor.getValue().getFillType()).isEqualTo(FillType.REJECTED);
        assertThat(captor.getValue().getExecutedQty()).isZero();
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
        return buildRequestWithSymbolAndType(orderType, side, qty, lastPrice, limitPrice, "AAPL");
    }

    private OrderExecutionRequest buildRequestWithSymbol(String symbol, int qty,
                                                         BigDecimal lastPrice, BigDecimal limitPrice) {
        return buildRequestWithSymbolAndType(OrderType.MARKET, OrderSide.BUY, qty, lastPrice, limitPrice, symbol);
    }

    private OrderExecutionRequest buildRequestWithSymbolAndType(OrderType orderType, OrderSide side,
                                                                int qty, BigDecimal lastPrice,
                                                                BigDecimal limitPrice, String symbol) {
        OrderExecutionRequest req = new OrderExecutionRequest();
        req.setOrderId(ORDER_ID);
        req.setClientId(CLIENT_ID);
        req.setSymbol(symbol);
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