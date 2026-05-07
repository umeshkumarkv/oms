package com.brokerdealer.executionservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionCompletedEvent {
    private UUID orderId;
    private UUID executionId;
    private UUID clientId;
    private String symbol;
    private String side;
    private Integer executedQty;
    private String executedPrice;
    private String fillType;
    private String fillValue;
    private Integer remainingQuantity;
    private String marketPriceAtFill;
    private String executionVenue;
    private String executedAt;
}
