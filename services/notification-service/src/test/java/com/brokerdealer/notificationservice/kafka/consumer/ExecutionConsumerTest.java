package com.brokerdealer.notificationservice.kafka.consumer;

import com.brokerdealer.notificationservice.domain.NotificationType;
import com.brokerdealer.notificationservice.dto.event.ExecutionCompletedEvent;
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
class ExecutionConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ExecutionConsumer consumer;

    @Test
    void shouldPushFullFillNotificationWithCorrectTitle() {
        ExecutionCompletedEvent event = fullFillEvent();
        ArgumentCaptor<NotificationResponse> captor = ArgumentCaptor.forClass(NotificationResponse.class);

        consumer.consumeExecutionCompleted(event);

        verify(notificationService).pushToClient(any(), captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Order Filled");
    }

    @Test
    void shouldPushFullFillNotificationWithCorrectMessage() {
        ExecutionCompletedEvent event = fullFillEvent();
        ArgumentCaptor<NotificationResponse> captor = ArgumentCaptor.forClass(NotificationResponse.class);

        consumer.consumeExecutionCompleted(event);

        verify(notificationService).pushToClient(any(), captor.capture());
        String message = captor.getValue().getMessage();
        assertThat(message).contains("AAPL");
        assertThat(message).contains("BUY");
        assertThat(message).contains("100");
        assertThat(message).contains("175.00");
        assertThat(message).contains("Fully filled");
    }

    @Test
    void shouldPushPartialFillNotificationWithRemainingQuantity() {
        ExecutionCompletedEvent event = partialFillEvent();
        ArgumentCaptor<NotificationResponse> captor = ArgumentCaptor.forClass(NotificationResponse.class);

        consumer.consumeExecutionCompleted(event);

        verify(notificationService).pushToClient(any(), captor.capture());
        NotificationResponse notification = captor.getValue();
        assertThat(notification.getTitle()).isEqualTo("Partial Fill");
        assertThat(notification.getMessage()).contains("50 remaining");
    }

    @Test
    void shouldSetTypeToExecutionCompleted() {
        ExecutionCompletedEvent event = fullFillEvent();
        ArgumentCaptor<NotificationResponse> captor = ArgumentCaptor.forClass(NotificationResponse.class);

        consumer.consumeExecutionCompleted(event);

        verify(notificationService).pushToClient(any(), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.EXECUTION_COMPLETED);
    }

    @Test
    void shouldBroadcastOnExecution() {
        ExecutionCompletedEvent event = fullFillEvent();
        consumer.consumeExecutionCompleted(event);
        verify(notificationService).broadcast(any());
    }

    @Test
    void shouldSkipWhenClientIdIsNull() {
        ExecutionCompletedEvent event = fullFillEvent();
        event.setClientId(null);

        consumer.consumeExecutionCompleted(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    void shouldSkipWhenOrderIdIsNull() {
        ExecutionCompletedEvent event = fullFillEvent();
        event.setOrderId(null);

        consumer.consumeExecutionCompleted(event);

        verify(notificationService, never()).pushToClient(any(), any());
    }

    private ExecutionCompletedEvent fullFillEvent() {
        return new ExecutionCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "AAPL",
                "BUY",
                100,
                "175.00",
                "FULL",
                0,
                Instant.now()
        );
    }

    private ExecutionCompletedEvent partialFillEvent() {
        return new ExecutionCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "AAPL",
                "BUY",
                50,
                "175.00",
                "PARTIAL",
                50,
                Instant.now()
        );
    }
}
