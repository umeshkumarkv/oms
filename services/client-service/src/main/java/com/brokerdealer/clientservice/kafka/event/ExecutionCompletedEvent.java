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
public class ExecutionCompletedEvent {

    private UUID executionId;
    private UUID orderId;
    private UUID clientId;
    private String symbol;
    /** BUY or SELL */
    private String side;
    private Integer fillQuantity;
    /** String representation of BigDecimal fill price */
    private String fillPrice;
    /** FULL, PARTIAL, or REJECTED */
    private String fillType;
    private Instant executedAt;
}
