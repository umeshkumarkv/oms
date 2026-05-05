package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InstrumentSummaryResponse {
    String symbol;
    String bidPrice;
    String askPrice;
    String lastPrice;
    String previousClose;
    String dataTimestamp;
    Boolean tradable;
    Boolean halted;
}
