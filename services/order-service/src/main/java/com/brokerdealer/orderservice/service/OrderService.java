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
import com.brokerdealer.orderservice.dto.response.StatusCountsResponse;
import com.brokerdealer.orderservice.dto.response.SubmitOrderData;
import com.brokerdealer.orderservice.dto.response.SubmitOrderResponse;
import com.brokerdealer.orderservice.exception.OrderAccessDeniedException;
import com.brokerdealer.orderservice.exception.ClientInactiveException;
import com.brokerdealer.orderservice.exception.ClientNotEligibleException;
import com.brokerdealer.orderservice.exception.InsufficientBuyingPowerException;
import com.brokerdealer.orderservice.exception.InsufficientHoldingsException;
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
import java.time.format.DateTimeParseException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final int GTD_DEFAULT_EXPIRY_DAYS = 180;
    private static final ZoneId ET_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(16, 0);
    private static final List<OrderStatus> OPEN_STATUSES = List.of(
            OrderStatus.NEW, OrderStatus.VALIDATED, OrderStatus.RISK_CHECK_PASSED,
            OrderStatus.ACCEPTED, OrderStatus.SENT_TO_EXECUTION, OrderStatus.PARTIALLY_FILLED);

    private static final int MAX_PAGE_SIZE = 200;
    private static final String ORDER_CACHE_KEY_PREFIX = "order:state:";
    private static final String STATUS_COUNTS_CACHE_KEY = "dashboard:status-counts:today";
    private static final Duration STATUS_COUNTS_TTL = Duration.ofSeconds(15);

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

        // Check 2: client exists
        ClientResponse clientResponse = clientServiceClient.getClient(request.getClientId());
        if (clientResponse == null || clientResponse.getData() == null) {
            throw new ClientNotFoundException(request.getClientId().toString());
        }
        // Check 3: client status must be ACTIVE
        if (!"ACTIVE".equals(clientResponse.getData().getStatus())) {
            log.warn("Order rejected — client account inactive: clientId={}", request.getClientId());
            throw new ClientInactiveException(request.getClientId().toString());
        }
        // Check 4: trading must be enabled
        if (!Boolean.TRUE.equals(clientResponse.getData().getTradingEnabled())) {
            throw new ClientNotEligibleException("trading is disabled for this account");
        }
        // Check 5: KYC must be VERIFIED and not expired
        String kycStatus = clientResponse.getData().getKycStatus();
        LocalDate kycExpiry = clientResponse.getData().getKycExpiryDate();
        if (!"VERIFIED".equals(kycStatus) || kycExpiry == null || !kycExpiry.isAfter(LocalDate.now())) {
            throw new ClientNotEligibleException("KYC is not verified or has expired");
        }

        // Check 6+7: symbol exists, tradable, and not halted
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

        // --- Compute portfolio snapshot fields for risk.evaluation.requested (SCRUM-119) ---
        OrderSide requestedSide = OrderSide.valueOf(request.getSide());
        OrderSide oppositeSide = requestedSide == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
        List<Order> oppositeOrders = orderRepository.findByClientIdAndSymbolAndSideAndStatusIn(
                request.getClientId(), request.getSymbol(), oppositeSide, OPEN_STATUSES);
        // capture limitPrice in an effectively final variable for use in the lambda
        final BigDecimal crossingPrice = limitPrice;
        boolean hasCrossingOpenOrder = oppositeOrders.stream().anyMatch(o -> {
            if (crossingPrice == null) return true;
            if (o.getLimitPrice() == null) return true;
            return requestedSide == OrderSide.BUY
                    ? o.getLimitPrice().compareTo(crossingPrice) <= 0
                    : o.getLimitPrice().compareTo(crossingPrice) >= 0;
        });

        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        int dailyOrderCount = (int) orderRepository.countDailyOrdersByClientId(
                request.getClientId(), startOfDay);

        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        List<Object[]> metricsList = orderRepository.computeAvgMetricsByClientId(request.getClientId(), cutoff);
        Object[] metrics = metricsList.isEmpty() ? new Object[]{null, null} : metricsList.get(0);
        BigDecimal avgOrderQty = metrics[0] != null
                ? BigDecimal.valueOf(((Number) metrics[0]).doubleValue()).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4);
        BigDecimal avgOrderValue = metrics[1] != null
                ? BigDecimal.valueOf(((Number) metrics[1]).doubleValue()).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4);

        // Read settled cashBalance from client-service portfolio.
        // For BUY orders, in-flight commitments are subtracted below to produce availableCash.
        BigDecimal cashBalance = clientResponse.getData().getCashBalance() != null
                ? new BigDecimal(clientResponse.getData().getCashBalance()) : BigDecimal.ZERO;
        List<ClientResponse.HoldingData> holdings = clientResponse.getData().getHoldings();
        int holdingsForSymbol = (holdings == null) ? 0 : holdings.stream()
                .filter(h -> h.getSymbol().equals(request.getSymbol()))
                .mapToInt(ClientResponse.HoldingData::getQuantity)
                .findFirst()
                .orElse(0);
        Integer maxDailyOrders = clientResponse.getData().getMaxOpenOrders();

        // --- L2 checks #20-21: holdings validation — SELL only, SHORT_SELL intentionally bypasses ---
        // availableToSell = settled holdings (client-service) - in-flight SELL commitments (own DB)
        // This prevents double-sell: two concurrent SELLs cannot collectively exceed actual holdings.
        if (requestedSide == OrderSide.SELL) {
            int committedToOpenSell = (int) orderRepository.sumOpenSellQuantity(
                    request.getClientId(), request.getSymbol(), OPEN_STATUSES);
            int availableToSell = holdingsForSymbol - committedToOpenSell;
            if (availableToSell <= 0) {
                throw new InsufficientHoldingsException();
            }
            if (availableToSell < request.getQuantity()) {
                throw new InsufficientHoldingsException();
            }
        }

        // --- L2 check #13: BUY — availableCash = settled cashBalance - in-flight BUY commitments ---
        // Combines both views: client-service (settled) + own DB (in-flight open BUY orders).
        // Replaces the PATCH /reserved-cash call. Client-service no longer owns in-flight state.
        BigDecimal availableCash = cashBalance;
        if (requestedSide == OrderSide.BUY) {
            BigDecimal committedBuyValue = orderRepository.sumOpenBuyOrderValues(
                    request.getClientId(), OPEN_STATUSES);
            if (committedBuyValue == null) committedBuyValue = BigDecimal.ZERO;
            availableCash = cashBalance.subtract(committedBuyValue).setScale(2, RoundingMode.HALF_UP);
            if (availableCash.compareTo(orderValue) < 0) {
                throw new InsufficientBuyingPowerException();
            }
        }

        // --- Create and persist order in NEW state ---
        BigDecimal lastPrice = new BigDecimal(instrumentResponse.getData().getLastPrice());

        Order order = Order.builder()
                .clientOrderId(request.getClientOrderId())
                .clientId(request.getClientId())
                .symbol(request.getSymbol())
                .side(OrderSide.valueOf(request.getSide()))
                .orderType(OrderType.valueOf(request.getOrderType()))
                .timeInForce(tif)
                .quantity(request.getQuantity())
                .limitPrice(limitPrice)
                .lastPrice(lastPrice)
                .orderValue(orderValue)
                .status(OrderStatus.NEW)
                .expiryDate(expiryDate)
                .expiryApplied(expiryApplied)
                .createdBy(userId != null ? userId : UUID.randomUUID())
                .routingDestination(resolveRoutingDestination(request.getRoutingDestination()))
                .fillOrKill(Boolean.TRUE.equals(request.getFillOrKill()))
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
                .buyingPower(availableCash.toPlainString())
                .holdingsForSymbol(holdingsForSymbol)
                .kycStatus(clientResponse.getData().getKycStatus())
                .kycExpiryDate(clientResponse.getData().getKycExpiryDate() != null
                        ? clientResponse.getData().getKycExpiryDate().toString() : null)
                .tradingEnabled(clientResponse.getData().getTradingEnabled())
                .lastPrice(instrumentResponse.getData().getLastPrice())
                .hasCrossingOpenOrder(hasCrossingOpenOrder)
                .dailyOrderCount(dailyOrderCount)
                .maxDailyOrders(maxDailyOrders)
                .avgOrderQty(avgOrderQty.toPlainString())
                .avgOrderValue(avgOrderValue.toPlainString())
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
            throw new ValidationException("INVALID_ORDER_SIDE", "Invalid side: must be BUY, SELL, or SHORT_SELL");
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

        // fillOrKill rules
        if (Boolean.TRUE.equals(request.getFillOrKill()) && orderType != OrderType.LIMIT) {
            throw new ValidationException("fillOrKill", "FILL_OR_KILL_REQUIRES_LIMIT",
                    "fillOrKill is only supported for LIMIT orders");
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

    public OrderDetailResponse getOrderById(UUID id, UUID callerUserId, String callerRole) {
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

        if ("TRADER".equals(callerRole)) {
            if (callerUserId == null || !callerUserId.equals(order.getClientId())) {
                log.warn("Access denied: orderId={} callerRole={}", id, callerRole);
                throw new OrderAccessDeniedException("ACCESS_DENIED",
                        "TRADER may only access their own client's orders");
            }
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
                                       String createdAfter, int page, int pageSize) {
        if (timeInForce != null && !timeInForce.isBlank()) {
            try {
                TimeInForce.valueOf(timeInForce);
            } catch (IllegalArgumentException e) {
                throw new ValidationException("VALIDATION_ERROR",
                        "Invalid tif filter value: must be DAY or GTD");
            }
        }

        Instant createdAfterInstant = null;
        if (createdAfter != null && !createdAfter.isBlank()) {
            try {
                createdAfterInstant = Instant.parse(createdAfter);
            } catch (DateTimeParseException e) {
                throw new ValidationException("VALIDATION_ERROR",
                        "Invalid createdAfter format: must be ISO 8601 UTC (e.g. 2026-05-18T00:00:00Z)");
            }
        }

        int validatedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int validatedPage = Math.max(page, 0);

        Specification<Order> spec = buildOrderSpec(status, clientId, side, timeInForce, createdAfterInstant);
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

    private Specification<Order> buildOrderSpec(String status, UUID clientId, String side, String timeInForce,
                                                Instant createdAfterInstant) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                if ("OPEN".equalsIgnoreCase(status)) {
                    List<OrderStatus> terminalStates = List.of(
                            OrderStatus.RISK_REJECTED, OrderStatus.FILLED,
                            OrderStatus.CANCELLED, OrderStatus.REJECTED, OrderStatus.EXPIRED);
                    predicates.add(root.get("status").in(terminalStates).not());
                } else {
                    try {
                        predicates.add(cb.equal(root.get("status"), OrderStatus.valueOf(status)));
                    } catch (IllegalArgumentException ignored) {}
                }
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
            if (createdAfterInstant != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfterInstant));
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
                .routingDestination(order.getRoutingDestination())
                .build();
    }

    private String resolveRoutingDestination(String requested) {
        return (requested != null && !requested.isBlank()) ? requested : "NYSE";
    }

    public StatusCountsResponse getStatusCountsForToday() {
        // --- Cache-aside: try Redis first ---
        try {
            Object cached = redisTemplate.opsForValue().get(STATUS_COUNTS_CACHE_KEY);
            if (cached instanceof Map<?, ?> rawMap) {
                log.debug("Cache hit: statusCounts key={}", STATUS_COUNTS_CACHE_KEY);
                Map<String, Integer> counts = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    int val = entry.getValue() instanceof Number n ? n.intValue() : 0;
                    counts.put(entry.getKey().toString(), val);
                }
                return StatusCountsResponse.builder()
                        .data(counts)
                        .meta(ResponseMeta.builder()
                                .timestamp(Instant.now().toString())
                                .requestId(UUID.randomUUID().toString())
                                .build())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Redis read failed for statusCounts: {}", e.getMessage());
        }

        // --- Cache miss: query DB with today's ET date boundary ---
        LocalDate today = LocalDate.now(ET_ZONE);
        Instant startOfDay = today.atStartOfDay(ET_ZONE).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(ET_ZONE).toInstant();

        List<Object[]> rows = orderRepository.countByStatusForDateRange(startOfDay, endOfDay);

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s.name(), 0);
        }
        for (Object[] row : rows) {
            OrderStatus status = (OrderStatus) row[0];
            long count = ((Number) row[1]).longValue();
            counts.put(status.name(), (int) count);
        }

        // --- Populate cache ---
        try {
            redisTemplate.opsForValue().set(STATUS_COUNTS_CACHE_KEY, counts, STATUS_COUNTS_TTL);
            log.debug("Cache populated: statusCounts key={}", STATUS_COUNTS_CACHE_KEY);
        } catch (Exception e) {
            log.warn("Redis write failed for statusCounts: {}", e.getMessage());
        }

        return StatusCountsResponse.builder()
                .data(counts)
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }
}
