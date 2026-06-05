package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InstrumentSearchResponse {
    String symbol;
    String companyName;
    String exchange;
    String cusip;
    String secNumber;
    String lastPrice;
    Boolean tradable;
}