package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class MoverEntryResponse {
    String symbol;
    String percentChange;
}
