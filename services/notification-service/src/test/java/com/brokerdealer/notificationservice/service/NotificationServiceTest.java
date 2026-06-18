package com.brokerdealer.notificationservice.service;

import com.brokerdealer.notificationservice.domain.NotificationType;
import com.brokerdealer.notificationservice.dto.response.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void shouldPushToUserViaRegistry() {
        UUID clientId = UUID.randomUUID();
        NotificationResponse notification = buildNotification(clientId);

        notificationService.pushToClient(clientId, notification);

        verify(sseEmitterRegistry).pushToUser(eq(clientId), any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldBroadcastViaRegistry() {
        UUID clientId = UUID.randomUUID();
        NotificationResponse notification = buildNotification(clientId);

        notificationService.broadcast(notification);

        verify(sseEmitterRegistry).broadcast(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldBuildEventWithCorrectNameForPush() {
        UUID clientId = UUID.randomUUID();
        NotificationResponse notification = buildNotification(clientId);

        notificationService.pushToClient(clientId, notification);

        ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(sseEmitterRegistry).pushToUser(eq(clientId), captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void shouldBuildEventWithCorrectNameForBroadcast() {
        UUID clientId = UUID.randomUUID();
        NotificationResponse notification = buildNotification(clientId);

        notificationService.broadcast(notification);

        ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(sseEmitterRegistry).broadcast(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    private NotificationResponse buildNotification(UUID clientId) {
        return NotificationResponse.builder()
                .type(NotificationType.ORDER_STATE_CHANGED)
                .orderId(UUID.randomUUID())
                .clientId(clientId)
                .title("Test")
                .message("Test message")
                .timestamp(Instant.now())
                .build();
    }
}
