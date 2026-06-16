package com.brokerdealer.orderservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TradingSummaryData {
    String turnoverToday;
    Integer ordersToday;
    String fillRate;
    String buySideNotional;
    String sellSideNotional;
}
