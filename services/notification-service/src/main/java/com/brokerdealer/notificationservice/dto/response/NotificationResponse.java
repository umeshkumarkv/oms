package com.brokerdealer.notificationservice.dto.response;

import com.brokerdealer.notificationservice.domain.NotificationType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class NotificationResponse {

    NotificationType type;
    UUID orderId;
    UUID clientId;
    String title;
    String message;
    Instant timestamp;
}