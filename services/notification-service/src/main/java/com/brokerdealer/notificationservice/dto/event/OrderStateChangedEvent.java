package com.brokerdealer.notificationservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStateChangedEvent {

    private UUID orderId;
    private UUID clientId;
    private UUID userId;
    private String fromState;
    private String toState;
    private String triggeredBy;
    private String timestamp;
}
