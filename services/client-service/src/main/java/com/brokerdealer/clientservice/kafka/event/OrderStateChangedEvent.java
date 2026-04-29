package com.brokerdealer.clientservice.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStateChangedEvent {

    private UUID orderId;
    private UUID clientId;
    /** BUY or SELL */
    private String side;
    private String newState;
    private String previousState;
    /** String representation of BigDecimal: quantity × limit/market price */
    private String orderValue;
    private String actorId;
    private String actorType;
    private Instant timestamp;
}
