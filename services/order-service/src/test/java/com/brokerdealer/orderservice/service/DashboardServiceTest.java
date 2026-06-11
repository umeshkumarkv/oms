package com.brokerdealer.orderservice.service;

import com.brokerdealer.orderservice.domain.OrderStatus;
import com.brokerdealer.orderservice.dto.response.RiskAlertsResponse;
import com.brokerdealer.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private DashboardService dashboardService;

    private static final BigDecimal NEAR_LIMIT_THRESHOLD = new BigDecimal("800000");
    private static final BigDecimal LARGE_ORDER_THRESHOLD = new BigDecimal("500000");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dashboardService, "clientsNearLimitThreshold", NEAR_LIMIT_THRESHOLD);
        ReflectionTestUtils.setField(dashboardService, "largeOrderThreshold", LARGE_ORDER_THRESHOLD);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldReturnZeroCountsWhenNoOrdersExistToday() {
        when(valueOperations.get("dashboard:risk-alerts:today")).thenReturn(null);
        when(orderRepository.findClientIdsWithHighNotionalToday(any(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.countByStatusAndDateRange(eq(OrderStatus.RISK_REJECTED), any(), any()))
                .thenReturn(0L);
        when(orderRepository.countActiveOrdersByStatusesAndMinOrderValue(any(), any()))
                .thenReturn(0L);
        when(orderRepository.countByStatusAndUpdatedAtBefore(eq(OrderStatus.SENT_TO_EXECUTION), any()))
                .thenReturn(0L);

        RiskAlertsResponse response = dashboardService.getRiskAlerts();

        assertThat(response.getData().getClientsNearLimit()).isZero();
        assertThat(response.getData().getCashRejectCount()).isZero();
        assertThat(response.getData().getLargeOrdersAwaitingReview()).isZero();
        assertThat(response.getData().getPendingVenueAck()).isZero();
        assertThat(response.getMeta().getTimestamp()).isNotNull();
        assertThat(response.getMeta().getRequestId()).isNotNull();
    }

    @Test
    void shouldReturnCorrectClientNearLimitCount() {
        when(valueOperations.get("dashboard:risk-alerts:today")).thenReturn(null);
        UUID client1 = UUID.randomUUID();
        UUID client2 = UUID.randomUUID();
        when(orderRepository.findClientIdsWithHighNotionalToday(any(), any(), any()))
                .thenReturn(List.of(client1, client2));
        when(orderRepository.countByStatusAndDateRange(any(), any(), any())).thenReturn(0L);
        when(orderRepository.countActiveOrdersByStatusesAndMinOrderValue(any(), any())).thenReturn(0L);
        when(orderRepository.countByStatusAndUpdatedAtBefore(any(), any())).thenReturn(0L);

        RiskAlertsResponse response = dashboardService.getRiskAlerts();

        assertThat(response.getData().getClientsNearLimit()).isEqualTo(2);
    }

    @Test
    void shouldReturnZeroClientsNearLimitWhenAllBelowThreshold() {
        when(valueOperations.get("dashboard:risk-alerts:today")).thenReturn(null);
        when(orderRepository.findClientIdsWithHighNotionalToday(any(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.countByStatusAndDateRange(any(), any(), any())).thenReturn(0L);
        when(orderRepository.countActiveOrdersByStatusesAndMinOrderValue(any(), any())).thenReturn(0L);
        when(orderRepository.countByStatusAndUpdatedAtBefore(any(), any())).thenReturn(0L);

        RiskAlertsResponse response = dashboardService.getRiskAlerts();

        assertThat(response.getData().getClientsNearLimit()).isZero();
    }

    @Test
    void shouldCountRiskRejectedOrdersAsCashRejectCount() {
        when(valueOperations.get("dashboard:risk-alerts:today")).thenReturn(null);
        when(orderRepository.findClientIdsWithHighNotionalToday(any(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.countByStatusAndDateRange(eq(OrderStatus.RISK_REJECTED), any(), any()))
                .thenReturn(5L);
        when(orderRepository.countActiveOrdersByStatusesAndMinOrderValue(any(), any())).thenReturn(0L);
        when(orderRepository.countByStatusAndUpdatedAtBefore(any(), any())).thenReturn(0L);

        RiskAlertsResponse response = dashboardService.getRiskAlerts();

        assertThat(response.getData().getCashRejectCount()).isEqualTo(5);
    }

    @Test
    void shouldReturnZeroCashRejectCountWhenNoRejections() {
        when(valueOperations.get("dashboard:risk-alerts:today")).thenReturn(null);
        when(orderRepository.findClientIdsWithHighNotionalToday(any(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.countByStatusAndDateRange(any(), any(), any())).thenReturn(0L);
        when(orderRepository.countActiveOrdersByStatusesAndMinOrderValue(any(), any())).thenReturn(0L);
        when(orderRepository.countByStatusAndUpdatedAtBefore(any(), any())).thenReturn(0L);

        RiskAlertsResponse response = dashboardService.getRiskAlerts();

        assertThat(response.getData().getCashRejectCount()).isZero();
    }

    @Test
    void shouldCountActiveLargeOrdersAboveThreshold() {
        when(valueOperations.get("dashboard:risk-alerts:today")).thenReturn(null);
        when(orderRepository.findClientIdsWithHighNotionalToday(any(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.countByStatusAndDateRange(any(), any(), any())).thenReturn(0L);
        when(orderRepository.countActiveOrdersByStatusesAndMinOrderValue(any(), any())).thenReturn(3L);
        when(orderRepository.countByStatusAndUpdatedAtBefore(any(), any())).thenReturn(0L);

        RiskAlertsResponse response = dashboardService.getRiskAlerts();

        assertThat(response.getData().getLargeOrdersAwaitingReview()).isEqualTo(3);
    }

    @Test
    void shouldExcludeTerminalOrdersFromLargeOrderCount() {
        when(valueOperations.get("dashboard:risk-alerts:today")).thenReturn(null);
        when(orderRepository.findClientIdsWithHighNotionalToday(any(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.countByStatusAndDateRange(any(), any(), any())).thenReturn(0L);
        // Only ACCEPTED and SENT_TO_EXECUTION are passed — terminal states are not included
        when(orderRepository.countActiveOrdersByStatusesAndMinOrderValue(
                eq(List.of(OrderStatus.ACCEPTED, OrderStatus.SENT_TO_EXECUTION)), any()))
                .thenReturn(2L);
        when(orderRepository.countByStatusAndUpdatedAtBefore(any(), any())).thenReturn(0L);

        RiskAlertsResponse response = dashboardService.getRiskAlerts();

        assertThat(response.getData().getLargeOrdersAwaitingReview()).isEqualTo(2);
    }

    @Test
    void shouldCountPendingVenueAckOrdersOlderThan30Seconds() {
        when(valueOperations.get("dashboard:risk-alerts:today")).thenReturn(null);
        when(orderRepository.findClientIdsWithHighNotionalToday(any(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.countByStatusAndDateRange(any(), any(), any())).thenReturn(0L);
        when(orderRepository.countActiveOrdersByStatusesAndMinOrderValue(any(), any())).thenReturn(0L);
        when(orderRepository.countByStatusAndUpdatedAtBefore(eq(OrderStatus.SENT_TO_EXECUTION), any()))
                .thenReturn(4L);

        RiskAlertsResponse response = dashboardService.getRiskAlerts();

        assertThat(response.getData().getPendingVenueAck()).isEqualTo(4);
    }

    @Test
    void shouldReturnCachedResultWhenRedisCacheHit() {
        Map<String, Integer> cachedData = Map.of(
                "clientsNearLimit", 1,
                "cashRejectCount", 2,
                "largeOrdersAwaitingReview", 3,
                "pendingVenueAck", 4
        );
        when(valueOperations.get("dashboard:risk-alerts:today")).thenReturn(cachedData);

        RiskAlertsResponse response = dashboardService.getRiskAlerts();

        assertThat(response.getData().getClientsNearLimit()).isEqualTo(1);
        assertThat(response.getData().getCashRejectCount()).isEqualTo(2);
        assertThat(response.getData().getLargeOrdersAwaitingReview()).isEqualTo(3);
        assertThat(response.getData().getPendingVenueAck()).isEqualTo(4);
        // No DB queries when cache hits
        verify(orderRepository, never()).findClientIdsWithHighNotionalToday(any(), any(), any());
    }

    @Test
    void shouldGracefullyDegradeWhenRedisUnavailable() {
        when(valueOperations.get("dashboard:risk-alerts:today"))
                .thenThrow(new RuntimeException("Redis connection refused"));
        when(orderRepository.findClientIdsWithHighNotionalToday(any(), any(), any()))
                .thenReturn(List.of(UUID.randomUUID()));
        when(orderRepository.countByStatusAndDateRange(any(), any(), any())).thenReturn(1L);
        when(orderRepository.countActiveOrdersByStatusesAndMinOrderValue(any(), any())).thenReturn(1L);
        when(orderRepository.countByStatusAndUpdatedAtBefore(any(), any())).thenReturn(1L);

        RiskAlertsResponse response = dashboardService.getRiskAlerts();

        // DB result still returned despite Redis error
        assertThat(response.getData().getClientsNearLimit()).isEqualTo(1);
        assertThat(response.getData().getCashRejectCount()).isEqualTo(1);
    }
}
