package com.brokerdealer.orderservice.service;

import com.brokerdealer.orderservice.domain.Order;
import com.brokerdealer.orderservice.domain.OrderStatus;
import com.brokerdealer.orderservice.dto.kafka.OrderExecutionRequestedEvent;
import com.brokerdealer.orderservice.dto.kafka.OrderStateChangedEvent;
import com.brokerdealer.orderservice.exception.OrderNotFoundException;
import com.brokerdealer.orderservice.kafka.producer.OrderEventProducer;
import com.brokerdealer.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStateMachineService {

    /**
     * States that are terminal or already past the risk evaluation phase.
     * Used ONLY as an idempotency guard in risk.evaluation.result consumers.
     * SENT_TO_EXECUTION is NOT a true terminal state per the order lifecycle —
     * do NOT reuse this set in consumeExecutionCompleted (SCRUM-48).
     */
    private static final Set<OrderStatus> IDEMPOTENT_SKIP_STATES_FOR_RISK_RESULT = Set.of(
            OrderStatus.FILLED, OrderStatus.CANCELLED, OrderStatus.REJECTED,
            OrderStatus.EXPIRED, OrderStatus.RISK_REJECTED, OrderStatus.SENT_TO_EXECUTION
    );

    private static final String TRIGGERED_BY_RISK_ENGINE = "SYSTEM:RISK_ENGINE";
    private static final String TRIGGERED_BY_OMS = "SYSTEM:OMS";

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public void transitionOnRiskPassed(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (IDEMPOTENT_SKIP_STATES_FOR_RISK_RESULT.contains(order.getStatus())) {
            log.warn("Idempotency guard — order already in terminal/target state: orderId={} status={}",
                    orderId, order.getStatus());
            return;
        }

        if (order.getStatus() != OrderStatus.NEW) {
            log.warn("Unexpected state for risk-passed event: orderId={} status={}", orderId, order.getStatus());
            return;
        }

        transitionState(order, OrderStatus.NEW, OrderStatus.VALIDATED, TRIGGERED_BY_RISK_ENGINE);
        transitionState(order, OrderStatus.VALIDATED, OrderStatus.RISK_CHECK_PASSED, TRIGGERED_BY_RISK_ENGINE);
        transitionState(order, OrderStatus.RISK_CHECK_PASSED, OrderStatus.ACCEPTED, TRIGGERED_BY_OMS);
        transitionState(order, OrderStatus.ACCEPTED, OrderStatus.SENT_TO_EXECUTION, TRIGGERED_BY_OMS);

        orderEventProducer.publishOrderExecutionRequested(OrderExecutionRequestedEvent.builder()
                .orderId(order.getId())
                .clientId(order.getClientId())
                .symbol(order.getSymbol())
                .side(order.getSide().name())
                .orderType(order.getOrderType().name())
                .quantity(order.getQuantity())
                .limitPrice(order.getLimitPrice())
                .timeInForce(order.getTimeInForce().name())
                .orderValue(order.getOrderValue())
                .lastPrice(order.getLastPrice())
                .build());

        evictRedisOrderState(orderId);
        log.info("Order reached SENT_TO_EXECUTION: orderId={}", orderId);
    }

    @Transactional
    public void transitionOnRiskRejected(UUID orderId, String rejectionReason, String violatedRuleCode) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (IDEMPOTENT_SKIP_STATES_FOR_RISK_RESULT.contains(order.getStatus())) {
            log.warn("Idempotency guard — order already in terminal state: orderId={} status={}",
                    orderId, order.getStatus());
            return;
        }

        if (order.getStatus() != OrderStatus.NEW) {
            log.warn("Unexpected state for risk-rejected event: orderId={} status={}", orderId, order.getStatus());
            return;
        }

        OrderStatus fromState = order.getStatus();
        order.setStatus(OrderStatus.RISK_REJECTED);
        order.setRejectionReason(rejectionReason);
        orderRepository.save(order);

        orderEventProducer.publishOrderStateChanged(OrderStateChangedEvent.builder()
                .orderId(order.getId())
                .clientId(order.getClientId())
                .userId(order.getCreatedBy())
                .fromState(fromState.name())
                .toState(OrderStatus.RISK_REJECTED.name())
                .triggeredBy(TRIGGERED_BY_RISK_ENGINE)
                .timestamp(Instant.now().toString())
                .metadata(Map.of(
                        "rejectionReason",  rejectionReason  != null ? rejectionReason  : "",
                        "violatedRuleCode", violatedRuleCode != null ? violatedRuleCode : ""
                ))
                .build());

        log.info("Order state transition: orderId={} {} -> {}", orderId, fromState, OrderStatus.RISK_REJECTED);

        evictRedisOrderState(orderId);
        log.info("Order risk rejected: orderId={} violatedRuleCode={}", orderId, violatedRuleCode);
    }

    private static final String TRIGGERED_BY_EXECUTION = "SYSTEM:EXECUTION_ENGINE";
    private static final Set<OrderStatus> EXECUTION_TERMINAL_STATES =
            Set.of(OrderStatus.FILLED, OrderStatus.REJECTED, OrderStatus.CANCELLED, OrderStatus.EXPIRED);

    @Transactional
    public void transitionOnExecutionFilled(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (EXECUTION_TERMINAL_STATES.contains(order.getStatus())) {
            log.warn("Idempotency: order already terminal: orderId={} status={}", orderId, order.getStatus());
            return;
        }
        transitionState(order, order.getStatus(), OrderStatus.FILLED, TRIGGERED_BY_EXECUTION);
        evictRedisOrderState(orderId);
        log.info("Order FILLED: orderId={}", orderId);
    }

    @Transactional
    public void transitionOnExecutionPartialFill(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (EXECUTION_TERMINAL_STATES.contains(order.getStatus()) ||
                order.getStatus() == OrderStatus.PARTIALLY_FILLED) {
            log.debug("Idempotency: skipping partial fill for orderId={} status={}", orderId, order.getStatus());
            return;
        }
        transitionState(order, order.getStatus(), OrderStatus.PARTIALLY_FILLED, TRIGGERED_BY_EXECUTION);
        evictRedisOrderState(orderId);
        log.info("Order PARTIALLY_FILLED: orderId={}", orderId);
    }

    @Transactional
    public void transitionOnExecutionRejected(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (EXECUTION_TERMINAL_STATES.contains(order.getStatus())) {
            log.warn("Idempotency: order already terminal: orderId={} status={}", orderId, order.getStatus());
            return;
        }
        transitionState(order, order.getStatus(), OrderStatus.REJECTED, TRIGGERED_BY_EXECUTION);
        evictRedisOrderState(orderId);
        log.info("Order REJECTED by execution: orderId={}", orderId);
    }

    private void transitionState(Order order, OrderStatus from, OrderStatus to, String triggeredBy) {
        order.setStatus(to);
        orderRepository.save(order);

        orderEventProducer.publishOrderStateChanged(OrderStateChangedEvent.builder()
                .orderId(order.getId())
                .clientId(order.getClientId())
                .userId(order.getCreatedBy())
                .fromState(from.name())
                .toState(to.name())
                .triggeredBy(triggeredBy)
                .timestamp(Instant.now().toString())
                .metadata(Collections.emptyMap())
                .build());

        log.info("Order state transition: orderId={} {} -> {}", order.getId(), from, to);
    }

    private void evictRedisOrderState(UUID orderId) {
        redisTemplate.delete("order:state:" + orderId);
    }
}
