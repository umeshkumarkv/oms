package com.brokerdealer.orderservice.service;

import com.brokerdealer.orderservice.client.ClientServiceClient;
import com.brokerdealer.orderservice.client.MarketDataServiceClient;
import com.brokerdealer.orderservice.domain.ExpiryApplied;
import com.brokerdealer.orderservice.domain.Order;
import com.brokerdealer.orderservice.domain.OrderSide;
import com.brokerdealer.orderservice.domain.OrderStatus;
import com.brokerdealer.orderservice.domain.OrderType;
import com.brokerdealer.orderservice.domain.TimeInForce;
import com.brokerdealer.orderservice.dto.client.ClientResponse;
import com.brokerdealer.orderservice.dto.kafka.OrderStateChangedEvent;
import com.brokerdealer.orderservice.dto.kafka.RiskEvaluationRequestedEvent;
import com.brokerdealer.orderservice.dto.market.InstrumentResponse;
import com.brokerdealer.orderservice.dto.market.MarketHoursResponse;
import com.brokerdealer.orderservice.dto.request.SubmitOrderRequest;
import com.brokerdealer.orderservice.dto.response.ResponseMeta;
import com.brokerdealer.orderservice.dto.response.SubmitOrderData;
import com.brokerdealer.orderservice.dto.response.SubmitOrderResponse;
import com.brokerdealer.orderservice.exception.ClientInactiveException;
import com.brokerdealer.orderservice.exception.ClientNotFoundException;
import com.brokerdealer.orderservice.exception.InvalidExpiryDateException;
import com.brokerdealer.orderservice.exception.MarketClosedException;
import com.brokerdealer.orderservice.exception.SymbolHaltedException;
import com.brokerdealer.orderservice.exception.SymbolNotFoundException;
import com.brokerdealer.orderservice.exception.ValidationException;
import com.brokerdealer.orderservice.kafka.producer.OrderEventProducer;
import com.brokerdealer.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final int GTC_DEFAULT_EXPIRY_DAYS = 180;

    private final OrderRepository orderRepository;
    private final ClientServiceClient clientServiceClient;
    private final MarketDataServiceClient marketDataServiceClient;
    private final OrderEventProducer orderEventProducer;

    @Transactional
    public SubmitOrderResponse submitOrder(SubmitOrderRequest request, UUID userId) {
        log.debug("Submitting order: clientId={} symbol={} side={} type={}",
                request.getClientId(), request.getSymbol(), request.getSide(), request.getOrderType());

        // --- LEVEL 1: Business validation (syntactic) ---
        validateLevel1(request);

        // --- LEVEL 2: External checks ---
        // Check 1: market hours
        MarketHoursResponse hoursResponse = marketDataServiceClient.getMarketHours();
        if (hoursResponse == null || hoursResponse.getData() == null
                || !Boolean.TRUE.equals(hoursResponse.getData().getMarketOpen())) {
            throw new MarketClosedException();
        }

        // Check 2+3: client exists and is active
        ClientResponse clientResponse = clientServiceClient.getClient(request.getClientId());
        if (clientResponse == null || clientResponse.getData() == null) {
            throw new ClientNotFoundException(request.getClientId().toString());
        }
        if (!"ACTIVE".equals(clientResponse.getData().getStatus())) {
            throw new ClientInactiveException(request.getClientId().toString());
        }

        // Check 4+5: symbol exists, tradable, and not halted
        InstrumentResponse instrumentResponse = marketDataServiceClient.getInstrument(request.getSymbol());
        if (instrumentResponse == null || instrumentResponse.getData() == null
                || !Boolean.TRUE.equals(instrumentResponse.getData().getTradable())) {
            throw new SymbolNotFoundException(request.getSymbol());
        }
        if (Boolean.TRUE.equals(instrumentResponse.getData().getHalted())) {
            throw new SymbolHaltedException(request.getSymbol());
        }

        // --- Set expiry ---
        LocalDate expiryDate = null;
        ExpiryApplied expiryApplied = null;
        TimeInForce tif = TimeInForce.valueOf(request.getTimeInForce());

        if (tif == TimeInForce.GTC) {
            if (request.getExpiryDate() != null) {
                validateExpiryDate(request.getExpiryDate());
                expiryDate = request.getExpiryDate();
                expiryApplied = ExpiryApplied.TRADER;
            } else {
                expiryDate = LocalDate.now().plusDays(GTC_DEFAULT_EXPIRY_DAYS);
                expiryApplied = ExpiryApplied.DEFAULT;
            }
        }

        // --- Create and persist order in NEW state ---
        BigDecimal limitPrice = null;
        if (request.getLimitPrice() != null && !request.getLimitPrice().isBlank()) {
            limitPrice = new BigDecimal(request.getLimitPrice());
        }

        Order order = Order.builder()
                .clientOrderId(request.getClientOrderId())
                .clientId(request.getClientId())
                .symbol(request.getSymbol())
                .side(OrderSide.valueOf(request.getSide()))
                .orderType(OrderType.valueOf(request.getOrderType()))
                .timeInForce(tif)
                .quantity(request.getQuantity())
                .limitPrice(limitPrice)
                .status(OrderStatus.NEW)
                .expiryDate(expiryDate)
                .expiryApplied(expiryApplied)
                .createdBy(userId != null ? userId : UUID.randomUUID())
                .build();

        Order savedOrder = orderRepository.save(order);
        log.info("Order created: orderId={} clientId={} symbol={} side={} status=NEW",
                savedOrder.getId(), savedOrder.getClientId(), savedOrder.getSymbol(), savedOrder.getSide());

        // --- Compute orderValue for risk evaluation ---
        BigDecimal orderValue;
        if (OrderType.valueOf(request.getOrderType()) == OrderType.LIMIT) {
            orderValue = new BigDecimal(request.getLimitPrice())
                    .multiply(BigDecimal.valueOf(request.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            // MARKET — use lastPrice already fetched from instrumentResponse
            orderValue = new BigDecimal(instrumentResponse.getData().getLastPrice())
                    .multiply(BigDecimal.valueOf(request.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // --- Publish order.state.changed (NEW) ---
        orderEventProducer.publishOrderStateChanged(OrderStateChangedEvent.builder()
                .orderId(savedOrder.getId())
                .clientId(savedOrder.getClientId())
                .fromState(null)
                .toState(OrderStatus.NEW.name())
                .triggeredBy("USER:" + userId)
                .timestamp(Instant.now().toString())
                .metadata(Collections.emptyMap())
                .build());

        // --- Publish risk.evaluation.requested ---
        orderEventProducer.publishRiskEvaluationRequested(RiskEvaluationRequestedEvent.builder()
                .orderId(savedOrder.getId())
                .clientId(savedOrder.getClientId())
                .symbol(savedOrder.getSymbol())
                .side(savedOrder.getSide().name())
                .orderType(savedOrder.getOrderType().name())
                .quantity(savedOrder.getQuantity())
                .limitPrice(savedOrder.getLimitPrice() != null ? savedOrder.getLimitPrice().toPlainString() : null)
                .timeInForce(savedOrder.getTimeInForce().name())
                .orderValue(orderValue.toPlainString())
                .build());

        return SubmitOrderResponse.builder()
                .data(SubmitOrderData.builder()
                        .orderId(savedOrder.getId())
                        .status(OrderStatus.NEW.name())
                        .expiryDate(savedOrder.getExpiryDate())
                        .expiryApplied(savedOrder.getExpiryApplied() != null
                                ? savedOrder.getExpiryApplied().name() : null)
                        .build())
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }

    private void validateLevel1(SubmitOrderRequest request) {
        // Validate side
        try {
            OrderSide.valueOf(request.getSide());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("VALIDATION_ERROR", "Invalid side: must be BUY or SELL");
        }

        // Validate orderType
        try {
            OrderType.valueOf(request.getOrderType());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("VALIDATION_ERROR", "Invalid orderType: must be MARKET or LIMIT");
        }

        // Validate timeInForce
        try {
            TimeInForce.valueOf(request.getTimeInForce());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("VALIDATION_ERROR", "Invalid timeInForce: must be DAY or GTC");
        }

        // limitPrice rules
        OrderType orderType = OrderType.valueOf(request.getOrderType());
        if (orderType == OrderType.LIMIT) {
            if (request.getLimitPrice() == null || request.getLimitPrice().isBlank()) {
                throw new ValidationException("LIMIT_PRICE_REQUIRED",
                        "limitPrice is required for LIMIT orders");
            }
            try {
                BigDecimal price = new BigDecimal(request.getLimitPrice());
                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ValidationException("VALIDATION_ERROR", "limitPrice must be greater than 0");
                }
            } catch (NumberFormatException e) {
                throw new ValidationException("VALIDATION_ERROR", "limitPrice must be a valid decimal number");
            }
        }
        if (orderType == OrderType.MARKET && request.getLimitPrice() != null && !request.getLimitPrice().isBlank()) {
            throw new ValidationException("LIMIT_PRICE_NOT_ALLOWED",
                    "limitPrice must not be provided for MARKET orders");
        }
    }

    private void validateExpiryDate(LocalDate expiryDate) {
        LocalDate today = LocalDate.now();
        if (!expiryDate.isAfter(today)) {
            throw new InvalidExpiryDateException("expiryDate must be a future date");
        }
        // Weekends are not valid expiry dates
        java.time.DayOfWeek day = expiryDate.getDayOfWeek();
        if (day == java.time.DayOfWeek.SATURDAY || day == java.time.DayOfWeek.SUNDAY) {
            throw new InvalidExpiryDateException("expiryDate must not be a weekend");
        }
    }

    public Optional<Object> getOrderById(UUID id) {
        // TODO: implement — SCRUM-44
        return Optional.empty();
    }

    public Object getOrders(String status, UUID clientId, int page, int size) {
        // TODO: implement — SCRUM-44
        return null;
    }
}
