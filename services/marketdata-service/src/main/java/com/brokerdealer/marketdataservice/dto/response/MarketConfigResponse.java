package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MarketConfigResponse {

    long   pollingIntervalMs;
    boolean marketOpen;
    MarketHoursConfig marketHours;
    String dataSource;   // "WEBSOCKET" | "POLLING"

    @Value
    @Builder
    public static class MarketHoursConfig {
        String start;
        String end;
        String timezone;
    }
}
