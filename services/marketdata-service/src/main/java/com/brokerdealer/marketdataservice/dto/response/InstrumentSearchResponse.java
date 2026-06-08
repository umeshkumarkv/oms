package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class InstrumentSearchResponse {
    String symbol;
    String companyName;
    String exchange;
    String cusip;
    String secNumber;
    String lastPrice;
    Boolean tradable;
}