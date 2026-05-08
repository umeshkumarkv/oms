package com.brokerdealer.notificationservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionCompletedEvent {

    private UUID executionId;
    private UUID orderId;
    private UUID clientId;
    private String symbol;
    private String side;
    private Integer executedQty;
    private String executedPrice;
    private String fillType;
    private Integer remainingQuantity;
    private Instant executedAt;
}
