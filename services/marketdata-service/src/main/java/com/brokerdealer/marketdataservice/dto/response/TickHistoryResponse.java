package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class TickHistoryResponse {
    String            symbol;
    String            from;    // ISO-8601: market open time today ET
    String            to;      // ISO-8601: current-minute floor ET
    List<TickResponse> ticks;
}
