package com.brokerdealer.orderservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FillResponse {
    String executionId;
    Integer executedQty;
    String executedPrice;
    String fillType;
    String executedAt;
}
