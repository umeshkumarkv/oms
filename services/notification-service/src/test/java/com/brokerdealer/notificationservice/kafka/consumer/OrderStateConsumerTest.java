package com.brokerdealer.notificationservice.kafka.consumer;

import com.brokerdealer.notificationservice.domain.NotificationType;
import com.brokerdealer.notificationservice.dto.event.OrderStateChangedEvent;
import com.brokerdealer.notificationservice.dto.response.NotificationResponse;
import com.brokerdealer.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderStateConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private OrderStateConsumer consumer;

    @Test
    void shouldPushToClientOnValidEvent() {
        OrderStateChangedEvent event = validEvent();
        consumer.consumeOrderStateChanged(event);
        verify(notificationService).pushToClient(eq(event.getClientId()), any());
    }

    @Test
    void shouldBroadcastOnValidEvent() {
        OrderStateChangedEvent event = validEvent();
        consumer.consumeOrderStateChanged(event);
        verify(notificationService).broadcast(any());
    }

    @Test
    void shouldSetTitleToOrderUpdate() {
        OrderStateChangedEvent event = validEvent();
        ArgumentCaptor<NotificationResponse> captor = ArgumentCaptor.forClass(NotificationResponse.class);

        consumer.consumeOrderStateChanged(event);

        verify(notificationService).pushToClient(any(), captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Order Update");
    }

    @Test
    void shouldSetTypeToOrderStateChanged() {
        OrderStateChangedEvent event = validEvent();
        ArgumentCaptor<NotificationResponse> captor = ArgumentCaptor.forClass(NotificationResponse.class);

        consumer.consumeOrderStateChanged(event);

        verify(notificationService).pushToClient(any(), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.ORDER_STATE_CHANGED);
    }

    @Test
    void shouldSetMessageWithPreviousAndNewState() {
        OrderStateChangedEvent event = validEvent();
        ArgumentCaptor<NotificationResponse> captor = ArgumentCaptor.forClass(NotificationResponse.class);

        consumer.consumeOrderStateChanged(event);

        verify(notificationService).pushToClient(any(), captor.capture());
        String message = captor.getValue().getMessage();
        assertThat(message).contains(event.getOrderId().toString());
        assertThat(message).contains("NEW");
        assertThat(message).contains("VALIDATED");
    }

    @Test
    void shouldSkipWhenClientIdIsNull() {
        OrderStateChangedEvent event = validEvent();
        event.setClientId(null);

        consumer.consumeOrderStateChanged(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    void shouldSkipWhenOrderIdIsNull() {
        OrderStateChangedEvent event = validEvent();
        event.setOrderId(null);

        consumer.consumeOrderStateChanged(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    void shouldNotCallPushWhenClientIdIsNull() {
        OrderStateChangedEvent event = validEvent();
        event.setClientId(null);

        consumer.consumeOrderStateChanged(event);

        verify(notificationService, never()).pushToClient(any(), any());
    }

    @Test
    void shouldNotCallBroadcastWhenOrderIdIsNull() {
        OrderStateChangedEvent event = validEvent();
        event.setOrderId(null);

        consumer.consumeOrderStateChanged(event);

        verify(notificationService, never()).broadcast(any());
    }

    private OrderStateChangedEvent validEvent() {
        return new OrderStateChangedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "NEW",
                "VALIDATED",
                "SYSTEM",
                Instant.now()
        );
    }
}
