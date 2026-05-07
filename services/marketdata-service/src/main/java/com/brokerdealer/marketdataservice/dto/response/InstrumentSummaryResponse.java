package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class InstrumentSummaryResponse {
    String symbol;
    String bidPrice;
    String askPrice;
    String lastPrice;
    String change;
    String changePct;
    Long volume;
    String dayHigh;
    String dayLow;
    String previousClose;
    List<String> priceHistory;
    String marketStatus;
    String dataTimestamp;
    Boolean tradable;
    Boolean halted;
}
