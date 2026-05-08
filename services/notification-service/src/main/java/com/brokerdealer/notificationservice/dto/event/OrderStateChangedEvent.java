package com.brokerdealer.notificationservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStateChangedEvent {

    private UUID orderId;
    private UUID clientId;
    private String previousState;
    private String newState;
    private String actorType;
    private Instant timestamp;
}
