package com.brokerdealer.orderservice.service;

import com.brokerdealer.orderservice.domain.Order;
import com.brokerdealer.orderservice.domain.OrderStatus;
import com.brokerdealer.orderservice.domain.TimeInForce;
import com.brokerdealer.orderservice.dto.kafka.OrderStateChangedEvent;
import com.brokerdealer.orderservice.kafka.producer.OrderEventProducer;
import com.brokerdealer.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketCloseScheduler {

    private static final String TRIGGERED_BY_SCHEDULER = "SYSTEM:SCHEDULER";
    private static final String TRIGGERED_BY_RECOVERY = "SYSTEM:RECOVERY";
    private static final String ORDER_CACHE_KEY_PREFIX = "order:state:";

    private static final List<LocalDate> US_MARKET_HOLIDAYS_2026 = List.of(
            LocalDate.of(2026, 1, 1),   // New Year's Day
            LocalDate.of(2026, 1, 19),  // MLK Day
            LocalDate.of(2026, 2, 16),  // Presidents Day
            LocalDate.of(2026, 4, 3),   // Good Friday
            LocalDate.of(2026, 5, 25),  // Memorial Day
            LocalDate.of(2026, 7, 3),   // Independence Day (observed)
            LocalDate.of(2026, 9, 7),   // Labor Day
            LocalDate.of(2026, 11, 26), // Thanksgiving
            LocalDate.of(2026, 12, 25)  // Christmas
    );

    private static final List<OrderStatus> EXPIRY_ELIGIBLE_STATUSES =
            List.of(OrderStatus.SENT_TO_EXECUTION, OrderStatus.PARTIALLY_FILLED);

    private static final List<OrderStatus> NON_TERMINAL_STATUSES = List.of(
            OrderStatus.NEW, OrderStatus.VALIDATED, OrderStatus.RISK_CHECK_PASSED,
            OrderStatus.ACCEPTED, OrderStatus.SENT_TO_EXECUTION, OrderStatus.PARTIALLY_FILLED
    );

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Clock clock;

    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "America/New_York")
    @Transactional
    public void runMarketCloseScheduler() {
        LocalDate today = LocalDate.now(clock);

        if (US_MARKET_HOLIDAYS_2026.contains(today)) {
            log.info("market close skipped — US market holiday: date={}", today);
            return;
        }

        List<Order> dayOrders = orderRepository.findByTimeInForceAndStatusIn(
                TimeInForce.DAY, EXPIRY_ELIGIBLE_STATUSES);
        dayOrders.forEach(o -> expireOrder(o, TRIGGERED_BY_SCHEDULER, "DAY order expired at market close"));

        List<Order> gtdOrders = orderRepository.findByTimeInForceAndExpiryDateAndStatusIn(
                TimeInForce.GTD, today, EXPIRY_ELIGIBLE_STATUSES);
        gtdOrders.forEach(o -> expireOrder(o, TRIGGERED_BY_SCHEDULER, "GTD order reached gtdDate at market close"));

        log.info("market close processed — {} DAY orders expired, {} GTD orders expired",
                dayOrders.size(), gtdOrders.size());
    }

    @Scheduled(cron = "0 30 9 * * MON-FRI", zone = "America/New_York")
    @Transactional
    public void runMarketOpenRecovery() {
        LocalDate today = LocalDate.now(clock);

        List<Order> staleGtdOrders = orderRepository.findByTimeInForceAndExpiryDateBeforeAndStatusIn(
                TimeInForce.GTD, today, NON_TERMINAL_STATUSES);

        if (staleGtdOrders.isEmpty()) {
            log.info("SYSTEM:RECOVERY — no stale GTD orders found");
            return;
        }

        staleGtdOrders.forEach(o -> {
            log.warn("SYSTEM:RECOVERY — stale GTD order found: orderId={} gtdDate={}", o.getId(), o.getExpiryDate());
            expireOrder(o, TRIGGERED_BY_RECOVERY, "SYSTEM:RECOVERY — GTD order missed at prior market close");
        });

        log.warn("SYSTEM:RECOVERY completed — {} stale GTD orders expired", staleGtdOrders.size());
    }

    private void expireOrder(Order order, String triggeredBy, String reason) {
        OrderStatus fromState = order.getStatus();
        order.setStatus(OrderStatus.EXPIRED);
        orderRepository.save(order);

        orderEventProducer.publishOrderStateChanged(OrderStateChangedEvent.builder()
                .orderId(order.getId())
                .clientId(order.getClientId())
                .fromState(fromState.name())
                .toState(OrderStatus.EXPIRED.name())
                .triggeredBy(triggeredBy)
                .timestamp(Instant.now().toString())
                .metadata(Map.of("reason", reason))
                .build());

        redisTemplate.delete(ORDER_CACHE_KEY_PREFIX + order.getId());
        log.info("Order expired: orderId={} from={} triggeredBy={}", order.getId(), fromState, triggeredBy);
    }
}
