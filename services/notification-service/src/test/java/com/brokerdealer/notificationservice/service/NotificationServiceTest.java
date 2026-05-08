package com.brokerdealer.notificationservice.service;

import com.brokerdealer.notificationservice.domain.NotificationType;
import com.brokerdealer.notificationservice.dto.response.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void shouldPushToClientDestination() {
        UUID clientId = UUID.randomUUID();
        NotificationResponse notification = buildNotification(clientId);

        notificationService.pushToClient(clientId, notification);

        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate).convertAndSend(destCaptor.capture(), (Object) org.mockito.ArgumentMatchers.eq(notification));
        assertThat(destCaptor.getValue()).isEqualTo("/topic/notifications/" + clientId);
    }

    @Test
    void shouldBroadcastToBroadcastDestination() {
        UUID clientId = UUID.randomUUID();
        NotificationResponse notification = buildNotification(clientId);

        notificationService.broadcast(notification);

        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate).convertAndSend(destCaptor.capture(), (Object) org.mockito.ArgumentMatchers.eq(notification));
        assertThat(destCaptor.getValue()).isEqualTo("/topic/notifications/broadcast");
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
