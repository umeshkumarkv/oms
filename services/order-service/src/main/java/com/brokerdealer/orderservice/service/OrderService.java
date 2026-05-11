package com.brokerdealer.orderservice.service;

import com.brokerdealer.orderservice.client.ClientServiceClient;
import com.brokerdealer.orderservice.client.ExecutionServiceClient;
import com.brokerdealer.orderservice.client.MarketDataServiceClient;
import com.brokerdealer.orderservice.domain.ExpiryApplied;
import com.brokerdealer.orderservice.domain.Order;
import com.brokerdealer.orderservice.domain.OrderSide;
import com.brokerdealer.orderservice.domain.OrderStatus;
import com.brokerdealer.orderservice.domain.OrderType;
import com.brokerdealer.orderservice.domain.TimeInForce;
import com.brokerdealer.orderservice.dto.client.ClientResponse;
import com.brokerdealer.orderservice.dto.execution.ExecutionItem;
import com.brokerdealer.orderservice.dto.kafka.OrderStateChangedEvent;
import com.brokerdealer.orderservice.dto.kafka.RiskEvaluationRequestedEvent;
import com.brokerdealer.orderservice.dto.market.InstrumentResponse;
import com.brokerdealer.orderservice.dto.market.MarketHoursResponse;
import com.brokerdealer.orderservice.dto.request.SubmitOrderRequest;
import com.brokerdealer.orderservice.dto.response.FillResponse;
import com.brokerdealer.orderservice.dto.response.OrderDetailResponse;
import com.brokerdealer.orderservice.dto.response.OrderListData;
import com.brokerdealer.orderservice.dto.response.OrderListResponse;
import com.brokerdealer.orderservice.dto.response.OrderResponse;
import com.brokerdealer.orderservice.dto.response.ResponseMeta;
import com.brokerdealer.orderservice.dto.response.SubmitOrderData;
import com.brokerdealer.orderservice.dto.response.SubmitOrderResponse;
import com.brokerdealer.orderservice.exception.ClientInactiveException;
import com.brokerdealer.orderservice.exception.ClientNotFoundException;
import com.brokerdealer.orderservice.exception.InvalidExpiryDateException;
import com.brokerdealer.orderservice.exception.MarketClosedException;
import com.brokerdealer.orderservice.exception.OrderNotFoundException;
import com.brokerdealer.orderservice.exception.SymbolHaltedException;
import com.brokerdealer.orderservice.exception.SymbolNotFoundException;
import com.brokerdealer.orderservice.exception.ValidationException;
import com.brokerdealer.orderservice.kafka.producer.OrderEventProducer;
import com.brokerdealer.orderservice.repository.OrderRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final int GTD_DEFAULT_EXPIRY_DAYS = 180;
    private static final ZoneId ET_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(16, 0);

    private static final int MAX_PAGE_SIZE = 200;
    private static final String ORDER_CACHE_KEY_PREFIX = "order:state:";

    private final OrderRepository orderRepository;
    private final ClientServiceClient clientServiceClient;
    private final MarketDataServiceClient marketDataServiceClient;
    private final OrderEventProducer orderEventProducer;
    private final ExecutionServiceClient executionServiceClient;
    private final RedisTemplate<String, Object> redisTemplate;

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

        if (tif == TimeInForce.GTD) {
            if (request.getExpiryDate() != null) {
                validateExpiryDate(request.getExpiryDate());
                expiryDate = request.getExpiryDate();
                expiryApplied = ExpiryApplied.TRADER;
            } else {
                expiryDate = LocalDate.now().plusDays(GTD_DEFAULT_EXPIRY_DAYS);
                expiryApplied = ExpiryApplied.DEFAULT;
            }
        }

        // --- Compute orderValue for risk evaluation and cash reservation ---
        BigDecimal limitPrice = null;
        if (request.getLimitPrice() != null && !request.getLimitPrice().isBlank()) {
            limitPrice = new BigDecimal(request.getLimitPrice());
        }

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

        // --- Reserve cash for BUY orders before persisting ---
        if (OrderSide.valueOf(request.getSide()) == OrderSide.BUY) {
            clientServiceClient.reserveCash(request.getClientId(), orderValue);
        }

        // --- Create and persist order in NEW state ---
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

        // --- Publish order.state.changed (NEW) ---
        orderEventProducer.publishOrderStateChanged(OrderStateChangedEvent.builder()
                .orderId(savedOrder.getId())
                .clientId(savedOrder.getClientId())
                .userId(savedOrder.getCreatedBy())
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
                .userId(savedOrder.getCreatedBy())
                .symbol(savedOrder.getSymbol())
                .side(savedOrder.getSide().name())
                .orderType(savedOrder.getOrderType().name())
                .quantity(savedOrder.getQuantity())
                .limitPrice(savedOrder.getLimitPrice() != null ? savedOrder.getLimitPrice().toPlainString() : null)
                .timeInForce(savedOrder.getTimeInForce().name())
                .orderValue(orderValue.toPlainString())
                .marginCallActive(clientResponse.getData().getMarginCallActive())
                .build());

        String expiresAt = null;
        if (tif == TimeInForce.GTD && savedOrder.getExpiryDate() != null) {
            expiresAt = ZonedDateTime.of(savedOrder.getExpiryDate(), MARKET_CLOSE_TIME, ET_ZONE)
                    .toInstant().toString();
        }

        return SubmitOrderResponse.builder()
                .data(SubmitOrderData.builder()
                        .orderId(savedOrder.getId())
                        .status(OrderStatus.NEW.name())
                        .tif(tif.name())
                        .expiryDate(savedOrder.getExpiryDate())
                        .expiryApplied(savedOrder.getExpiryApplied() != null
                                ? savedOrder.getExpiryApplied().name() : null)
                        .expiresAt(expiresAt)
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
            throw new ValidationException("VALIDATION_ERROR", "Invalid timeInForce: must be DAY or GTD");
        }

        // limitPrice rules
        OrderType orderType = OrderType.valueOf(request.getOrderType());
        if (orderType == OrderType.LIMIT) {
            if (request.getLimitPrice() == null || request.getLimitPrice().isBlank()) {
                throw new ValidationException("limitPrice", "LIMIT_PRICE_REQUIRED",
                        "limitPrice is required for LIMIT orders");
            }
            try {
                BigDecimal price = new BigDecimal(request.getLimitPrice());
                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ValidationException("limitPrice", "VALIDATION_ERROR", "limitPrice must be greater than 0");
                }
            } catch (NumberFormatException e) {
                throw new ValidationException("limitPrice", "VALIDATION_ERROR", "limitPrice must be a valid decimal number");
            }
        }
        if (orderType == OrderType.MARKET && request.getLimitPrice() != null && !request.getLimitPrice().isBlank()) {
            throw new ValidationException("limitPrice", "LIMIT_PRICE_NOT_ALLOWED",
                    "limitPrice must not be provided for MARKET orders");
        }
    }

    private void validateExpiryDate(LocalDate expiryDate) {
        LocalDate today = LocalDate.now();
        if (!expiryDate.isAfter(today)) {
            throw new InvalidExpiryDateException("gtdDate must be a future date (not today or past)");
        }
        java.time.DayOfWeek day = expiryDate.getDayOfWeek();
        if (day == java.time.DayOfWeek.SATURDAY || day == java.time.DayOfWeek.SUNDAY) {
            throw new InvalidExpiryDateException("gtdDate must not be a weekend");
        }
        List<String> holidays = marketDataServiceClient.getHolidays();
        if (holidays.contains(expiryDate.toString())) {
            throw new InvalidExpiryDateException("gtdDate must not be a US market holiday");
        }
    }

    public OrderDetailResponse getOrderById(UUID id) {
        String cacheKey = ORDER_CACHE_KEY_PREFIX + id;
        Order order = null;

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof Order cachedOrder) {
            log.debug("Cache hit for order: orderId={}", id);
            order = cachedOrder;
        }

        if (order == null) {
            order = orderRepository.findById(id)
                    .orElseThrow(() -> new OrderNotFoundException(id));
            redisTemplate.opsForValue().set(cacheKey, order);
            log.debug("Cache miss — populated: orderId={}", id);
        }

        List<ExecutionItem> fills = executionServiceClient.getFills(id);
        OrderResponse orderResponse = toOrderResponse(order, fills);

        return OrderDetailResponse.builder()
                .data(orderResponse)
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }

    public OrderListResponse getOrders(String status, UUID clientId, String side, String timeInForce,
                                       int page, int pageSize) {
        if (timeInForce != null && !timeInForce.isBlank()) {
            try {
                TimeInForce.valueOf(timeInForce);
            } catch (IllegalArgumentException e) {
                throw new ValidationException("VALIDATION_ERROR",
                        "Invalid tif filter value: must be DAY or GTD");
            }
        }

        int validatedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int validatedPage = Math.max(page, 0);

        Specification<Order> spec = buildOrderSpec(status, clientId, side, timeInForce);
        Page<Order> ordersPage = orderRepository.findAll(spec, PageRequest.of(validatedPage, validatedPageSize));

        List<OrderResponse> items = ordersPage.getContent().stream()
                .map(o -> toOrderResponse(o, Collections.emptyList()))
                .toList();

        return OrderListResponse.builder()
                .data(OrderListData.builder()
                        .orders(items)
                        .page(validatedPage)
                        .pageSize(validatedPageSize)
                        .totalCount(ordersPage.getTotalElements())
                        .totalPages(ordersPage.getTotalPages())
                        .build())
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }

    private Specification<Order> buildOrderSpec(String status, UUID clientId, String side, String timeInForce) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("status"), OrderStatus.valueOf(status)));
                } catch (IllegalArgumentException ignored) {}
            }
            if (clientId != null) {
                predicates.add(cb.equal(root.get("clientId"), clientId));
            }
            if (side != null && !side.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("side"), OrderSide.valueOf(side)));
                } catch (IllegalArgumentException ignored) {}
            }
            if (timeInForce != null && !timeInForce.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("timeInForce"), TimeInForce.valueOf(timeInForce)));
                } catch (IllegalArgumentException ignored) {}
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private OrderResponse toOrderResponse(Order order, List<ExecutionItem> fills) {
        int filledQuantity = fills.stream()
                .mapToInt(f -> f.getExecutedQty() != null ? f.getExecutedQty() : 0)
                .sum();

        String avgFillPrice = null;
        if (filledQuantity > 0) {
            BigDecimal totalValue = fills.stream()
                    .filter(f -> f.getExecutedPrice() != null && f.getExecutedQty() != null)
                    .map(f -> new BigDecimal(f.getExecutedPrice())
                            .multiply(BigDecimal.valueOf(f.getExecutedQty())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avgFillPrice = totalValue
                    .divide(BigDecimal.valueOf(filledQuantity), 4, RoundingMode.HALF_UP)
                    .toPlainString();
        }

        List<FillResponse> fillResponses = fills.stream()
                .map(f -> FillResponse.builder()
                        .executionId(f.getExecutionId())
                        .executedQty(f.getExecutedQty())
                        .executedPrice(f.getExecutedPrice())
                        .fillType(f.getFillType())
                        .executedAt(f.getExecutedAt())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .clientId(order.getClientId())
                .clientOrderId(order.getClientOrderId())
                .symbol(order.getSymbol())
                .side(order.getSide() != null ? order.getSide().name() : null)
                .orderType(order.getOrderType() != null ? order.getOrderType().name() : null)
                .timeInForce(order.getTimeInForce() != null ? order.getTimeInForce().name() : null)
                .quantity(order.getQuantity())
                .limitPrice(order.getLimitPrice() != null ? order.getLimitPrice().toPlainString() : null)
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .rejectionReason(order.getRejectionReason())
                .expiryDate(order.getExpiryDate())
                .expiryApplied(order.getExpiryApplied() != null ? order.getExpiryApplied().name() : null)
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : null)
                .updatedAt(order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null)
                .createdBy(order.getCreatedBy())
                .filledQuantity(filledQuantity)
                .avgFillPrice(avgFillPrice)
                .fills(fillResponses)
                .build();
    }
}
