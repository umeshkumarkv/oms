package com.brokerdealer.orderservice.dto.execution;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExecutionItem {
    private String executionId;
    private String orderId;
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
