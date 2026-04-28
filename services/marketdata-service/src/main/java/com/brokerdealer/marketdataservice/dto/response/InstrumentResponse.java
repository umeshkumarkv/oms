package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InstrumentResponse {
    String symbol;
    String bidPrice;
    String askPrice;
    String lastPrice;
    String dayHigh;
    String dayLow;
    String previousClose;
    Long volume;
    Boolean tradable;
    Boolean halted;
    Integer minQuantity;
    Integer maxQuantity;
    String dataTimestamp;
}

