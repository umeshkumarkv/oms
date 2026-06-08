package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MoverEntryResponse {
    String symbol;
    String percentChange;
}
