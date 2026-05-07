package com.brokerdealer.executionservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResponse {
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
