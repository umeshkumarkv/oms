package com.brokerdealer.orderservice.service;

import com.brokerdealer.orderservice.domain.OrderSide;
import com.brokerdealer.orderservice.domain.OrderStatus;
import com.brokerdealer.orderservice.dto.response.ResponseMeta;
import com.brokerdealer.orderservice.dto.response.RiskAlertsData;
import com.brokerdealer.orderservice.dto.response.RiskAlertsResponse;
import com.brokerdealer.orderservice.dto.response.TradingSummaryData;
import com.brokerdealer.orderservice.dto.response.TradingSummaryResponse;
import com.brokerdealer.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private static final ZoneId ET_ZONE = ZoneId.of("America/New_York");
    private static final String RISK_ALERTS_CACHE_KEY = "dashboard:risk-alerts:today";
    private static final Duration RISK_ALERTS_TTL = Duration.ofSeconds(15);
    private static final String SUMMARY_CACHE_KEY = "dashboard:summary:today";
    private static final Duration SUMMARY_TTL = Duration.ofSeconds(15);
    private static final List<OrderStatus> FILLED_STATUSES =
            List.of(OrderStatus.FILLED, OrderStatus.PARTIALLY_FILLED);
    private static final List<OrderSide> SELL_SIDES =
            List.of(OrderSide.SELL, OrderSide.SHORT_SELL);

    private final OrderRepository orderRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${dashboard.risk-alerts.clients-near-limit-threshold:800000}")
    private BigDecimal clientsNearLimitThreshold;

    @Value("${dashboard.risk-alerts.large-order-notional-threshold:500000}")
    private BigDecimal largeOrderThreshold;

    public RiskAlertsResponse getRiskAlerts() {
        try {
            Object cached = redisTemplate.opsForValue().get(RISK_ALERTS_CACHE_KEY);
            if (cached instanceof Map<?, ?> rawMap) {
                log.debug("Cache hit: riskAlerts key={}", RISK_ALERTS_CACHE_KEY);
                return buildResponseFromMap(rawMap);
            }
        } catch (Exception e) {
            log.warn("Redis read failed for riskAlerts: {}", e.getMessage());
        }

        RiskAlertsData data = computeRiskAlerts();

        try {
            Map<String, Integer> cacheValue = Map.of(
                    "clientsNearLimit", data.getClientsNearLimit(),
                    "cashRejectCount", data.getCashRejectCount(),
                    "largeOrdersAwaitingReview", data.getLargeOrdersAwaitingReview(),
                    "pendingVenueAck", data.getPendingVenueAck()
            );
            redisTemplate.opsForValue().set(RISK_ALERTS_CACHE_KEY, cacheValue, RISK_ALERTS_TTL);
            log.debug("Cache populated: riskAlerts key={}", RISK_ALERTS_CACHE_KEY);
        } catch (Exception e) {
            log.warn("Redis write failed for riskAlerts: {}", e.getMessage());
        }

        return buildResponse(data);
    }

    private RiskAlertsData computeRiskAlerts() {
        LocalDate today = LocalDate.now(ET_ZONE);
        Instant startOfDay = today.atStartOfDay(ET_ZONE).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(ET_ZONE).toInstant();

        List<UUID> highNotionalClients = orderRepository.findClientIdsWithHighNotionalToday(
                startOfDay, endOfDay, clientsNearLimitThreshold);
        int clientsNearLimit = highNotionalClients.size();

        long cashRejectCount = orderRepository.countByStatusAndDateRange(
                OrderStatus.RISK_REJECTED, startOfDay, endOfDay);

        List<OrderStatus> activeStatuses = List.of(OrderStatus.ACCEPTED, OrderStatus.SENT_TO_EXECUTION);
        long largeOrders = orderRepository.countActiveOrdersByStatusesAndMinOrderValue(
                activeStatuses, largeOrderThreshold);

        Instant cutoff = Instant.now().minusSeconds(30);
        long pendingAck = orderRepository.countByStatusAndUpdatedAtBefore(
                OrderStatus.SENT_TO_EXECUTION, cutoff);

        return RiskAlertsData.builder()
                .clientsNearLimit(clientsNearLimit)
                .cashRejectCount((int) cashRejectCount)
                .largeOrdersAwaitingReview((int) largeOrders)
                .pendingVenueAck((int) pendingAck)
                .build();
    }

    private RiskAlertsResponse buildResponse(RiskAlertsData data) {
        return RiskAlertsResponse.builder()
                .data(data)
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }

    private RiskAlertsResponse buildResponseFromMap(Map<?, ?> rawMap) {
        RiskAlertsData data = RiskAlertsData.builder()
                .clientsNearLimit(toInt(rawMap.get("clientsNearLimit")))
                .cashRejectCount(toInt(rawMap.get("cashRejectCount")))
                .largeOrdersAwaitingReview(toInt(rawMap.get("largeOrdersAwaitingReview")))
                .pendingVenueAck(toInt(rawMap.get("pendingVenueAck")))
                .build();
        return buildResponse(data);
    }

    private int toInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    public TradingSummaryResponse getTradingSummary() {
        try {
            Object cached = redisTemplate.opsForValue().get(SUMMARY_CACHE_KEY);
            if (cached instanceof Map<?, ?> rawMap) {
                log.debug("Cache hit: summary key={}", SUMMARY_CACHE_KEY);
                return buildSummaryResponseFromMap(rawMap);
            }
        } catch (Exception e) {
            log.warn("Redis read failed for summary: {}", e.getMessage());
        }

        TradingSummaryData data = computeTradingSummary();

        try {
            Map<String, Object> cacheValue = Map.of(
                    "turnoverToday", data.getTurnoverToday(),
                    "ordersToday", data.getOrdersToday(),
                    "fillRate", data.getFillRate(),
                    "buySideNotional", data.getBuySideNotional(),
                    "sellSideNotional", data.getSellSideNotional()
            );
            redisTemplate.opsForValue().set(SUMMARY_CACHE_KEY, cacheValue, SUMMARY_TTL);
            log.debug("Cache populated: summary key={}", SUMMARY_CACHE_KEY);
        } catch (Exception e) {
            log.warn("Redis write failed for summary: {}", e.getMessage());
        }

        return buildSummaryResponse(data);
    }

    private TradingSummaryData computeTradingSummary() {
        LocalDate today = LocalDate.now(ET_ZONE);
        Instant startOfDay = today.atStartOfDay(ET_ZONE).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(ET_ZONE).toInstant();

        BigDecimal turnoverToday = orderRepository
                .sumOrderValueByStatusesForDateRange(FILLED_STATUSES, startOfDay, endOfDay);

        long ordersToday = orderRepository.countAllOrdersForDateRange(startOfDay, endOfDay);
        long filledCount = orderRepository
                .countOrdersByStatusesForDateRange(FILLED_STATUSES, startOfDay, endOfDay);

        BigDecimal fillRate = ordersToday == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(filledCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(ordersToday), 2, RoundingMode.HALF_UP);

        BigDecimal buySideNotional = orderRepository
                .sumOrderValueBySidesForDateRange(List.of(OrderSide.BUY), startOfDay, endOfDay);
        BigDecimal sellSideNotional = orderRepository
                .sumOrderValueBySidesForDateRange(SELL_SIDES, startOfDay, endOfDay);

        return TradingSummaryData.builder()
                .turnoverToday(turnoverToday.toPlainString())
                .ordersToday((int) ordersToday)
                .fillRate(fillRate.toPlainString())
                .buySideNotional(buySideNotional.toPlainString())
                .sellSideNotional(sellSideNotional.toPlainString())
                .build();
    }

    private TradingSummaryResponse buildSummaryResponse(TradingSummaryData data) {
        return TradingSummaryResponse.builder()
                .data(data)
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }

    private TradingSummaryResponse buildSummaryResponseFromMap(Map<?, ?> rawMap) {
        TradingSummaryData data = TradingSummaryData.builder()
                .turnoverToday(toStr(rawMap.get("turnoverToday")))
                .ordersToday(toInt(rawMap.get("ordersToday")))
                .fillRate(toStr(rawMap.get("fillRate")))
                .buySideNotional(toStr(rawMap.get("buySideNotional")))
                .sellSideNotional(toStr(rawMap.get("sellSideNotional")))
                .build();
        return buildSummaryResponse(data);
    }

    private String toStr(Object value) {
        return value != null ? String.valueOf(value) : "0";
    }
}
