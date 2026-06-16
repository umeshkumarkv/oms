package com.brokerdealer.orderservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TradingSummaryResponse {
    TradingSummaryData data;
    ResponseMeta meta;
}
