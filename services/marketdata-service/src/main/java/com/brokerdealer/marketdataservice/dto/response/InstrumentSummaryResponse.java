package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InstrumentSummaryResponse {
    String symbol;
    Boolean tradable;
    Boolean halted;
}
