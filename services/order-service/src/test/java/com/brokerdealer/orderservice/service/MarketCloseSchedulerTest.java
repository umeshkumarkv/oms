package com.brokerdealer.orderservice.service;

import com.brokerdealer.orderservice.domain.Order;
import com.brokerdealer.orderservice.domain.OrderSide;
import com.brokerdealer.orderservice.domain.OrderStatus;
import com.brokerdealer.orderservice.domain.OrderType;
import com.brokerdealer.orderservice.domain.TimeInForce;
import com.brokerdealer.orderservice.dto.kafka.OrderStateChangedEvent;
import com.brokerdealer.orderservice.kafka.producer.OrderEventProducer;
import com.brokerdealer.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketCloseSchedulerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderEventProducer orderEventProducer;
    @Mock private RedisTemplate<String, Object> redisTemplate;

    private static final ZoneId ET = ZoneId.of("America/New_York");

    // A non-holiday Tuesday in June 2026
    private static final LocalDate NORMAL_TRADING_DAY = LocalDate.of(2026, 6, 2);
    private static final Clock MARKET_CLOSE_CLOCK = Clock.fixed(
            ZonedDateTime.of(2026, 6, 2, 16, 0, 0, 0, ET).toInstant(), ET);

    private MarketCloseScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MarketCloseScheduler(
                orderRepository, orderEventProducer, redisTemplate, MARKET_CLOSE_CLOCK);
    }

    // --- Market close scheduler ---

    @Test
    void shouldExpireDayOrderInSentToExecution() {
        Order order = buildOrder(TimeInForce.DAY, OrderStatus.SENT_TO_EXECUTION, null);
        when(orderRepository.findByTimeInForceAndStatusIn(eq(TimeInForce.DAY), any()))
                .thenReturn(List.of(order));
        when(orderRepository.findByTimeInForceAndExpiryDateAndStatusIn(eq(TimeInForce.GTD), any(), any()))
                .thenReturn(Collections.emptyList());

        scheduler.runMarketCloseScheduler();

        verify(orderRepository).save(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        ArgumentCaptor<OrderStateChangedEvent> eventCaptor = ArgumentCaptor.forClass(OrderStateChangedEvent.class);
        verify(orderEventProducer).publishOrderStateChanged(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getToState()).isEqualTo("EXPIRED");
        assertThat(eventCaptor.getValue().getTriggeredBy()).isEqualTo("SYSTEM:SCHEDULER");
        assertThat(eventCaptor.getValue().getFromState()).isEqualTo("SENT_TO_EXECUTION");
    }

    @Test
    void shouldExpireDayOrderInPartiallyFilled() {
        Order order = buildOrder(TimeInForce.DAY, OrderStatus.PARTIALLY_FILLED, null);
        when(orderRepository.findByTimeInForceAndStatusIn(eq(TimeInForce.DAY), any()))
                .thenReturn(List.of(order));
        when(orderRepository.findByTimeInForceAndExpiryDateAndStatusIn(eq(TimeInForce.GTD), any(), any()))
                .thenReturn(Collections.emptyList());

        scheduler.runMarketCloseScheduler();

        verify(orderRepository).save(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        ArgumentCaptor<OrderStateChangedEvent> captor = ArgumentCaptor.forClass(OrderStateChangedEvent.class);
        verify(orderEventProducer).publishOrderStateChanged(captor.capture());
        assertThat(captor.getValue().getFromState()).isEqualTo("PARTIALLY_FILLED");
    }

    @Test
    void shouldExpireGtdOrderOnGtdDate() {
        Order order = buildOrder(TimeInForce.GTD, OrderStatus.SENT_TO_EXECUTION, NORMAL_TRADING_DAY);
        when(orderRepository.findByTimeInForceAndStatusIn(eq(TimeInForce.DAY), any()))
                .thenReturn(Collections.emptyList());
        when(orderRepository.findByTimeInForceAndExpiryDateAndStatusIn(
                eq(TimeInForce.GTD), eq(NORMAL_TRADING_DAY), any()))
                .thenReturn(List.of(order));

        scheduler.runMarketCloseScheduler();

        verify(orderRepository).save(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        ArgumentCaptor<OrderStateChangedEvent> captor = ArgumentCaptor.forClass(OrderStateChangedEvent.class);
        verify(orderEventProducer).publishOrderStateChanged(captor.capture());
        assertThat(captor.getValue().getTriggeredBy()).isEqualTo("SYSTEM:SCHEDULER");
        assertThat(captor.getValue().getMetadata()).containsKey("reason");
    }

    @Test
    void shouldNotExpireGtdOrderBeforeGtdDate() {
        LocalDate futureGtdDate = NORMAL_TRADING_DAY.plusDays(5);
        Order order = buildOrder(TimeInForce.GTD, OrderStatus.SENT_TO_EXECUTION, futureGtdDate);
        when(orderRepository.findByTimeInForceAndStatusIn(eq(TimeInForce.DAY), any()))
                .thenReturn(Collections.emptyList());
        // GTD query for today's date returns empty — order's gtdDate is in the future
        when(orderRepository.findByTimeInForceAndExpiryDateAndStatusIn(
                eq(TimeInForce.GTD), eq(NORMAL_TRADING_DAY), any()))
                .thenReturn(Collections.emptyList());

        scheduler.runMarketCloseScheduler();

        verify(orderRepository, never()).save(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SENT_TO_EXECUTION);
        verify(orderEventProducer, never()).publishOrderStateChanged(any());
    }

    @Test
    void shouldSkipOnUSMarketHoliday() {
        // 2026-01-01 is New Year's Day
        Clock holidayClock = Clock.fixed(
                ZonedDateTime.of(2026, 1, 1, 16, 0, 0, 0, ET).toInstant(), ET);
        MarketCloseScheduler holidayScheduler = new MarketCloseScheduler(
                orderRepository, orderEventProducer, redisTemplate, holidayClock);

        holidayScheduler.runMarketCloseScheduler();

        verify(orderRepository, never()).findByTimeInForceAndStatusIn(any(), any());
        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderStateChanged(any());
    }

    @Test
    void shouldProcessWithZeroEligibleOrders() {
        when(orderRepository.findByTimeInForceAndStatusIn(eq(TimeInForce.DAY), any()))
                .thenReturn(Collections.emptyList());
        when(orderRepository.findByTimeInForceAndExpiryDateAndStatusIn(eq(TimeInForce.GTD), any(), any()))
                .thenReturn(Collections.emptyList());

        scheduler.runMarketCloseScheduler();

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderStateChanged(any());
    }

    @Test
    void shouldExpireMultipleDayAndGtdOrders() {
        Order dayOrder1 = buildOrder(TimeInForce.DAY, OrderStatus.SENT_TO_EXECUTION, null);
        Order dayOrder2 = buildOrder(TimeInForce.DAY, OrderStatus.PARTIALLY_FILLED, null);
        Order gtdOrder = buildOrder(TimeInForce.GTD, OrderStatus.SENT_TO_EXECUTION, NORMAL_TRADING_DAY);

        when(orderRepository.findByTimeInForceAndStatusIn(eq(TimeInForce.DAY), any()))
                .thenReturn(List.of(dayOrder1, dayOrder2));
        when(orderRepository.findByTimeInForceAndExpiryDateAndStatusIn(
                eq(TimeInForce.GTD), eq(NORMAL_TRADING_DAY), any()))
                .thenReturn(List.of(gtdOrder));

        scheduler.runMarketCloseScheduler();

        verify(orderRepository, times(3)).save(any(Order.class));
        verify(orderEventProducer, times(3)).publishOrderStateChanged(any());
    }

    // --- SYSTEM:RECOVERY ---

    @Test
    void shouldRecoverStaleGtdOrders() {
        // An order whose gtdDate was yesterday — should have been expired but wasn't
        LocalDate yesterday = NORMAL_TRADING_DAY.minusDays(1);
        Order staleOrder = buildOrder(TimeInForce.GTD, OrderStatus.SENT_TO_EXECUTION, yesterday);
        when(orderRepository.findByTimeInForceAndExpiryDateBeforeAndStatusIn(
                eq(TimeInForce.GTD), eq(NORMAL_TRADING_DAY), any()))
                .thenReturn(List.of(staleOrder));

        scheduler.runMarketOpenRecovery();

        verify(orderRepository).save(staleOrder);
        assertThat(staleOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        ArgumentCaptor<OrderStateChangedEvent> captor = ArgumentCaptor.forClass(OrderStateChangedEvent.class);
        verify(orderEventProducer).publishOrderStateChanged(captor.capture());
        assertThat(captor.getValue().getTriggeredBy()).isEqualTo("SYSTEM:RECOVERY");
    }

    @Test
    void shouldLogNoStaleGtdOrdersWhenNoneFound() {
        when(orderRepository.findByTimeInForceAndExpiryDateBeforeAndStatusIn(
                eq(TimeInForce.GTD), eq(NORMAL_TRADING_DAY), any()))
                .thenReturn(Collections.emptyList());

        scheduler.runMarketOpenRecovery();

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderStateChanged(any());
    }

    // --- Helpers ---

    private Order buildOrder(TimeInForce tif, OrderStatus status, LocalDate expiryDate) {
        return Order.builder()
                .id(UUID.randomUUID())
                .clientId(UUID.randomUUID())
                .symbol("AAPL")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .timeInForce(tif)
                .quantity(10)
                .limitPrice(new BigDecimal("150.00"))
                .status(status)
                .expiryDate(expiryDate)
                .createdBy(UUID.randomUUID())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
