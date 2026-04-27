package com.brokerdealer.clientservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HoldingDetailResponse {
    String symbol;
    Integer quantity;
    String avgPrice;
    String currentPrice;
    String marketValue;
    String unrealisedPnL;
}
