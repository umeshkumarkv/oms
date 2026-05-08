package com.brokerdealer.notificationservice.kafka.consumer;

import com.brokerdealer.notificationservice.domain.NotificationType;
import com.brokerdealer.notificationservice.dto.event.RiskEvaluationResultEvent;
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
class RiskResultConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private RiskResultConsumer consumer;

    @Test
    void shouldPushRejectedNotificationWhenRiskRejected() {
        RiskEvaluationResultEvent event = rejectedEvent();
        consumer.consumeRiskResult(event);
        verify(notificationService).pushToClient(eq(event.getClientId()), any());
    }

    @Test
    void shouldBroadcastWhenRiskRejected() {
        RiskEvaluationResultEvent event = rejectedEvent();
        consumer.consumeRiskResult(event);
        verify(notificationService).broadcast(any());
    }

    @Test
    void shouldSetCorrectTitleAndTypeForRejected() {
        RiskEvaluationResultEvent event = rejectedEvent();
        ArgumentCaptor<NotificationResponse> captor = ArgumentCaptor.forClass(NotificationResponse.class);

        consumer.consumeRiskResult(event);

        verify(notificationService).pushToClient(any(), captor.capture());
        NotificationResponse notification = captor.getValue();
        assertThat(notification.getTitle()).isEqualTo("Order Rejected");
        assertThat(notification.getType()).isEqualTo(NotificationType.RISK_RESULT);
    }

    @Test
    void shouldIncludeFailedRuleAndReasonInMessage() {
        RiskEvaluationResultEvent event = rejectedEvent();
        ArgumentCaptor<NotificationResponse> captor = ArgumentCaptor.forClass(NotificationResponse.class);

        consumer.consumeRiskResult(event);

        verify(notificationService).pushToClient(any(), captor.capture());
        String message = captor.getValue().getMessage();
        assertThat(message).contains("ORDER_VALUE_LIMIT");
        assertThat(message).contains("Order value exceeds limit");
    }

    @Test
    void shouldNotPushWhenRiskResultIsPassed() {
        RiskEvaluationResultEvent event = passedEvent();
        consumer.consumeRiskResult(event);
        verifyNoInteractions(notificationService);
    }

    @Test
    void shouldNotBroadcastWhenRiskResultIsPassed() {
        RiskEvaluationResultEvent event = passedEvent();
        consumer.consumeRiskResult(event);
        verify(notificationService, never()).broadcast(any());
    }

    @Test
    void shouldSkipWhenClientIdIsNull() {
        RiskEvaluationResultEvent event = rejectedEvent();
        event.setClientId(null);

        consumer.consumeRiskResult(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    void shouldSkipWhenOrderIdIsNull() {
        RiskEvaluationResultEvent event = rejectedEvent();
        event.setOrderId(null);

        consumer.consumeRiskResult(event);

        verify(notificationService, never()).pushToClient(any(), any());
    }

    private RiskEvaluationResultEvent rejectedEvent() {
        return new RiskEvaluationResultEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "REJECTED",
                "ORDER_VALUE_LIMIT",
                "Order value exceeds limit",
                Instant.now()
        );
    }

    private RiskEvaluationResultEvent passedEvent() {
        return new RiskEvaluationResultEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PASSED",
                null,
                null,
                Instant.now()
        );
    }
}
