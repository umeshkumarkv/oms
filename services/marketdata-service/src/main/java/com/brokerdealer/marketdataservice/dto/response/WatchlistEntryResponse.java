package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WatchlistEntryResponse {

    String symbol;
    String addedAt;
}
