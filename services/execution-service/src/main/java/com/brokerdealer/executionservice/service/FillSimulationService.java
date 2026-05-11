package com.brokerdealer.executionservice.service;

import com.brokerdealer.executionservice.domain.*;
import com.brokerdealer.executionservice.dto.event.ExecutionCompletedEvent;
import com.brokerdealer.executionservice.dto.request.OrderExecutionRequest;
import com.brokerdealer.executionservice.dto.response.MarketDataResponse;
import com.brokerdealer.executionservice.kafka.producer.ExecutionEventProducer;
import com.brokerdealer.executionservice.repository.ExecutionRepository;
import com.brokerdealer.executionservice.repository.PendingOrderExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class FillSimulationService {

    private static final String EXECUTION_VENUE = "MOCK_ECN";

    private final ExecutionRepository executionRepository;
    private final PendingOrderExecutionRepository pendingRepository;
    private final ExecutionEventProducer executionEventProducer;
    private final MarketDataClient marketDataClient;

    // Symbol overrides (FAIL→REJECTED, SLOW→PARTIAL) take precedence over quantity ranges.
    FillType determineFillOutcome(OrderExecutionRequest request) {
        String symbol = request.getSymbol();
        if ("FAIL".equals(symbol)) return FillType.REJECTED;
        if ("SLOW".equals(symbol)) return FillType.PARTIAL;

        int qty = request.getQuantity();
        if (qty >= 1000 && qty <= 1003) return FillType.REJECTED;
        if (qty >= 10 && qty <= 99) return FillType.PARTIAL;
        return FillType.FULL;
    }

    @Transactional
    public void executeMarketFill(OrderExecutionRequest request) {
        executeFill(request, request.getLastPrice(), request.getLastPrice());
    }

    @Transactional
    public void initiateLimitFill(OrderExecutionRequest request) {
        boolean conditionMet = checkLimitCondition(request.getSide(), request.getLastPrice(), request.getLimitPrice());

        if (conditionMet) {
            executeFill(request, request.getLimitPrice(), request.getLastPrice());
        } else {
            PendingOrderExecution pending = PendingOrderExecution.builder()
                    .orderId(request.getOrderId())
                    .clientId(request.getClientId())
                    .symbol(request.getSymbol())
                    .side(request.getSide())
                    .orderType(OrderType.LIMIT)
                    .quantity(request.getQuantity())
                    .limitPrice(request.getLimitPrice())
                    .lastPrice(request.getLastPrice())
                    .orderValue(request.getOrderValue())
                    .status(PendingStatus.PENDING)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            pendingRepository.save(pending);
            log.info("LIMIT order queued for price monitoring: orderId={} symbol={} side={}",
                    request.getOrderId(), request.getSymbol(), request.getSide());
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void monitorPendingLimitOrders() {
        List<PendingOrderExecution> pending = pendingRepository.findByStatus(PendingStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        log.debug("Price monitor: checking {} pending LIMIT orders", pending.size());

        for (PendingOrderExecution order : pending) {
            Optional<MarketDataResponse> marketData = marketDataClient.getMarketData(order.getSymbol());
            if (marketData.isEmpty()) {
                log.warn("Price monitor: skipping orderId={} — marketdata unavailable for symbol={}",
                        order.getOrderId(), order.getSymbol());
                continue;
            }
            BigDecimal marketPrice = marketData.get().getLastPrice();
            if (marketPrice == null) {
                continue;
            }

            if (checkLimitCondition(order.getSide(), marketPrice, order.getLimitPrice())) {
                order.setStatus(PendingStatus.PROCESSED);
                order.setUpdatedAt(Instant.now());
                pendingRepository.save(order);

                OrderExecutionRequest req = toOrderExecutionRequest(order, marketPrice);
                executeFill(req, req.getLimitPrice(), marketPrice);
            }
        }
    }

    @Transactional
    public void executeFill(OrderExecutionRequest request, BigDecimal executedPrice, BigDecimal marketPrice) {
        FillType outcome = determineFillOutcome(request);
        switch (outcome) {
            case FULL -> {
                BigDecimal fillValue = computeFillValue(request.getQuantity(), executedPrice);
                Execution execution = buildExecution(request, request.getQuantity(), executedPrice,
                        FillType.FULL, fillValue, 0, marketPrice);
                executionRepository.save(execution);
                executionEventProducer.publishExecutionCompleted(toEvent(execution));
                log.info("FULL fill: orderId={} symbol={} executedQty={}",
                        request.getOrderId(), request.getSymbol(), request.getQuantity());
            }
            case PARTIAL -> executePartialFill(request, executedPrice, marketPrice);
            case REJECTED -> executeRejectedFill(request, marketPrice);
        }
    }

    @Transactional
    public void executePartialFill(OrderExecutionRequest request, BigDecimal executedPrice, BigDecimal marketPrice) {
        int tranche1Qty = (int) Math.floor(request.getQuantity() * 0.60);
        int tranche2Qty = request.getQuantity() - tranche1Qty;

        BigDecimal fillValue1 = computeFillValue(tranche1Qty, executedPrice);
        Execution tranche1 = buildExecution(request, tranche1Qty, executedPrice,
                FillType.PARTIAL, fillValue1, tranche2Qty, marketPrice);
        executionRepository.save(tranche1);
        executionEventProducer.publishExecutionCompleted(toEvent(tranche1));
        log.info("PARTIAL fill tranche1: orderId={} executedQty={} remainingQty={}",
                request.getOrderId(), tranche1Qty, tranche2Qty);

        executeTrancheTwo(request, tranche2Qty, executedPrice, marketPrice);
    }

    @Transactional
    public void executeRejectedFill(OrderExecutionRequest request, BigDecimal marketPrice) {
        Execution rejection = buildExecution(request, 0, marketPrice,
                FillType.REJECTED, BigDecimal.ZERO, request.getQuantity(), marketPrice);
        executionRepository.save(rejection);
        executionEventProducer.publishExecutionCompleted(toEvent(rejection));
        log.info("REJECTED fill: orderId={} symbol={} qty={}",
                request.getOrderId(), request.getSymbol(), request.getQuantity());
    }

    @Async
    @Transactional
    public void executeTrancheTwo(OrderExecutionRequest request, int qty,
                                  BigDecimal executedPrice, BigDecimal marketPrice) {
        long delayMs = ThreadLocalRandom.current().nextLong(2000, 5001);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Tranche2 sleep interrupted: orderId={}", request.getOrderId());
            return;
        }

        BigDecimal fillValue2 = computeFillValue(qty, executedPrice);
        Execution tranche2 = buildExecution(request, qty, executedPrice,
                FillType.FULL, fillValue2, 0, marketPrice);
        executionRepository.save(tranche2);
        executionEventProducer.publishExecutionCompleted(toEvent(tranche2));
        log.info("PARTIAL fill tranche2: orderId={} executedQty={} remainingQty=0",
                request.getOrderId(), qty);
    }

    public boolean checkLimitCondition(OrderSide side, BigDecimal marketPrice, BigDecimal limitPrice) {
        if (side == OrderSide.BUY) {
            return marketPrice.compareTo(limitPrice) <= 0;
        } else {
            return marketPrice.compareTo(limitPrice) >= 0;
        }
    }

    private BigDecimal computeFillValue(int qty, BigDecimal price) {
        return BigDecimal.valueOf(qty).multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    private Execution buildExecution(OrderExecutionRequest request, int qty, BigDecimal executedPrice,
                                     FillType fillType, BigDecimal fillValue,
                                     int remainingQty, BigDecimal marketPrice) {
        return Execution.builder()
                .orderId(request.getOrderId())
                .clientId(request.getClientId())
                .symbol(request.getSymbol())
                .side(request.getSide())
                .executedQty(qty)
                .executedPrice(executedPrice)
                .fillType(fillType)
                .fillValue(fillValue)
                .remainingQuantity(remainingQty)
                .marketPriceAtFill(marketPrice)
                .executionVenue(EXECUTION_VENUE)
                .executedAt(Instant.now())
                .build();
    }

    private ExecutionCompletedEvent toEvent(Execution execution) {
        return ExecutionCompletedEvent.builder()
                .orderId(execution.getOrderId())
                .executionId(execution.getId())
                .clientId(execution.getClientId())
                .symbol(execution.getSymbol())
                .side(execution.getSide().name())
                .executedQty(execution.getExecutedQty())
                .executedPrice(execution.getExecutedPrice().toPlainString())
                .fillType(execution.getFillType().name())
                .fillValue(execution.getFillValue().toPlainString())
                .remainingQuantity(execution.getRemainingQuantity())
                .marketPriceAtFill(execution.getMarketPriceAtFill().toPlainString())
                .executionVenue(execution.getExecutionVenue())
                .executedAt(execution.getExecutedAt().toString())
                .build();
    }

    private OrderExecutionRequest toOrderExecutionRequest(PendingOrderExecution pending, BigDecimal marketPrice) {
        OrderExecutionRequest req = new OrderExecutionRequest();
        req.setOrderId(pending.getOrderId());
        req.setClientId(pending.getClientId());
        req.setSymbol(pending.getSymbol());
        req.setSide(pending.getSide());
        req.setOrderType(pending.getOrderType());
        req.setQuantity(pending.getQuantity());
        req.setLimitPrice(pending.getLimitPrice());
        req.setOrderValue(pending.getOrderValue());
        req.setLastPrice(marketPrice);
        return req;
    }
}