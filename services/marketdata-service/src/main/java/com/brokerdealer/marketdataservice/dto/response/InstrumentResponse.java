package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder(toBuilder = true)
@Jacksonized
public class InstrumentResponse {
    String symbol;
    String bidPrice;
    String askPrice;
    String lastPrice;
    String change;
    String changePct;
    Long volume;
    String dayHigh;
    String dayLow;
    String open;
    String previousClose;
    String marketStatus;
    Boolean tradable;
    Boolean halted;
    Integer minQuantity;
    Integer maxQuantity;
    String dataTimestamp;
}
