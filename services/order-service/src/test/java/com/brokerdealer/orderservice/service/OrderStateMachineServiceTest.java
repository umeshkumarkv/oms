package com.brokerdealer.orderservice.service;

import com.brokerdealer.orderservice.domain.Order;
import com.brokerdealer.orderservice.domain.OrderStatus;
import com.brokerdealer.orderservice.dto.kafka.OrderStateChangedEvent;
import com.brokerdealer.orderservice.kafka.producer.OrderEventProducer;
import com.brokerdealer.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderStateMachineServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderEventProducer orderEventProducer;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private OrderStateMachineService orderStateMachineService;

    private final UUID orderId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    private Order newOrder() {
        return Order.builder()
                .id(orderId)
                .clientId(clientId)
                .symbol("AAPL")
                .status(OrderStatus.NEW)
                .build();
    }

    // --- transitionOnRiskPassed ---

    @Test
    void shouldTransitionThroughAllFourStatesWhenRiskPassed() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(newOrder()));

        // Capture the status at each save — Order is mutable, so snapshot each call
        List<OrderStatus> capturedStatuses = new ArrayList<>();
        doAnswer(inv -> {
            Order o = inv.getArgument(0);
            capturedStatuses.add(o.getStatus());
            return o;
        }).when(orderRepository).save(any(Order.class));

        orderStateMachineService.transitionOnRiskPassed(orderId);

        assertThat(capturedStatuses).containsExactly(
                OrderStatus.VALIDATED,
                OrderStatus.RISK_CHECK_PASSED,
                OrderStatus.ACCEPTED,
                OrderStatus.SENT_TO_EXECUTION
        );
    }

    @Test
    void shouldPublishFourOrderStateChangedEventsOnPassedResult() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(newOrder()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderStateMachineService.transitionOnRiskPassed(orderId);

        ArgumentCaptor<OrderStateChangedEvent> eventCaptor = ArgumentCaptor.forClass(OrderStateChangedEvent.class);
        verify(orderEventProducer, times(4)).publishOrderStateChanged(eventCaptor.capture());

        List<OrderStateChangedEvent> events = eventCaptor.getAllValues();
        assertThat(events.get(0).getFromState()).isEqualTo("NEW");
        assertThat(events.get(0).getToState()).isEqualTo("VALIDATED");
        assertThat(events.get(0).getTriggeredBy()).isEqualTo("SYSTEM:RISK_ENGINE");

        assertThat(events.get(1).getFromState()).isEqualTo("VALIDATED");
        assertThat(events.get(1).getToState()).isEqualTo("RISK_CHECK_PASSED");
        assertThat(events.get(1).getTriggeredBy()).isEqualTo("SYSTEM:RISK_ENGINE");

        assertThat(events.get(2).getFromState()).isEqualTo("RISK_CHECK_PASSED");
        assertThat(events.get(2).getToState()).isEqualTo("ACCEPTED");
        assertThat(events.get(2).getTriggeredBy()).isEqualTo("SYSTEM:OMS");

        assertThat(events.get(3).getFromState()).isEqualTo("ACCEPTED");
        assertThat(events.get(3).getToState()).isEqualTo("SENT_TO_EXECUTION");
        assertThat(events.get(3).getTriggeredBy()).isEqualTo("SYSTEM:OMS");
    }

    @Test
    void shouldEvictRedisKeyAfterPassedTransition() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(newOrder()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderStateMachineService.transitionOnRiskPassed(orderId);

        verify(redisTemplate).delete("order:state:" + orderId);
    }

    // --- transitionOnRiskRejected ---

    @Test
    void shouldTransitionToRiskRejectedAndPopulateRejectionReason() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(newOrder()));

        List<OrderStatus> capturedStatuses = new ArrayList<>();
        List<String> capturedReasons = new ArrayList<>();
        doAnswer(inv -> {
            Order o = inv.getArgument(0);
            capturedStatuses.add(o.getStatus());
            capturedReasons.add(o.getRejectionReason());
            return o;
        }).when(orderRepository).save(any(Order.class));

        orderStateMachineService.transitionOnRiskRejected(orderId, "SINGLE_ORDER_QTY_LIMIT", "Qty 15000 exceeds 10000");

        assertThat(capturedStatuses).containsExactly(OrderStatus.RISK_REJECTED);
        assertThat(capturedReasons).containsExactly("SINGLE_ORDER_QTY_LIMIT: Qty 15000 exceeds 10000");
    }

    @Test
    void shouldPublishOneOrderStateChangedEventOnRejectedResult() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(newOrder()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderStateMachineService.transitionOnRiskRejected(orderId, "ORDER_VALUE_LIMIT", "Value exceeds 500000");

        ArgumentCaptor<OrderStateChangedEvent> eventCaptor = ArgumentCaptor.forClass(OrderStateChangedEvent.class);
        verify(orderEventProducer).publishOrderStateChanged(eventCaptor.capture());

        OrderStateChangedEvent event = eventCaptor.getValue();
        assertThat(event.getFromState()).isEqualTo("NEW");
        assertThat(event.getToState()).isEqualTo("RISK_REJECTED");
        assertThat(event.getTriggeredBy()).isEqualTo("SYSTEM:RISK_ENGINE");
    }

    @Test
    void shouldEvictRedisKeyAfterRejectedTransition() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(newOrder()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderStateMachineService.transitionOnRiskRejected(orderId, "RULE", "reason");

        verify(redisTemplate).delete("order:state:" + orderId);
    }

    // --- Idempotency guards ---

    @Test
    void shouldIgnorePassedResultWhenOrderAlreadyInSentToExecution() {
        Order order = newOrder();
        order.setStatus(OrderStatus.SENT_TO_EXECUTION);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderStateMachineService.transitionOnRiskPassed(orderId);

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderStateChanged(any());
    }

    @Test
    void shouldIgnoreRejectedResultWhenOrderAlreadyRiskRejected() {
        Order order = newOrder();
        order.setStatus(OrderStatus.RISK_REJECTED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderStateMachineService.transitionOnRiskRejected(orderId, "RULE", "reason");

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderStateChanged(any());
    }

    @Test
    void shouldIgnorePassedResultWhenOrderInTerminalFilledState() {
        Order order = newOrder();
        order.setStatus(OrderStatus.FILLED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderStateMachineService.transitionOnRiskPassed(orderId);

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderStateChanged(any());
    }

    @Test
    void shouldIgnorePassedResultWhenOrderInTerminalRejectedState() {
        Order order = newOrder();
        order.setStatus(OrderStatus.REJECTED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderStateMachineService.transitionOnRiskPassed(orderId);

        verify(orderRepository, never()).save(any());
    }

    // --- Error paths ---

    @Test
    void shouldThrowWhenOrderNotFoundOnPassedResult() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderStateMachineService.transitionOnRiskPassed(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ORDER_NOT_FOUND");
    }

    @Test
    void shouldThrowWhenOrderNotFoundOnRejectedResult() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderStateMachineService.transitionOnRiskRejected(orderId, "RULE", "reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ORDER_NOT_FOUND");
    }

    @Test
    void shouldNotTransitionWhenOrderInUnexpectedStateOnPassedResult() {
        Order order = newOrder();
        order.setStatus(OrderStatus.VALIDATED); // unexpected — not NEW
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderStateMachineService.transitionOnRiskPassed(orderId);

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderStateChanged(any());
    }

    @Test
    void shouldIncludeOrderIdAndClientIdInEveryStateChangedEvent() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(newOrder()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderStateMachineService.transitionOnRiskPassed(orderId);

        ArgumentCaptor<OrderStateChangedEvent> captor = ArgumentCaptor.forClass(OrderStateChangedEvent.class);
        verify(orderEventProducer, times(4)).publishOrderStateChanged(captor.capture());

        captor.getAllValues().forEach(event -> {
            assertThat(event.getOrderId()).isEqualTo(orderId);
            assertThat(event.getClientId()).isEqualTo(clientId);
            assertThat(event.getTimestamp()).isNotNull();
        });
    }
}
