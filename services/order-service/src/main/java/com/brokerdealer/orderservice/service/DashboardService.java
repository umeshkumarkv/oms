package com.brokerdealer.orderservice.service;

import com.brokerdealer.orderservice.domain.OrderStatus;
import com.brokerdealer.orderservice.dto.response.ResponseMeta;
import com.brokerdealer.orderservice.dto.response.RiskAlertsData;
import com.brokerdealer.orderservice.dto.response.RiskAlertsResponse;
import com.brokerdealer.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
}
