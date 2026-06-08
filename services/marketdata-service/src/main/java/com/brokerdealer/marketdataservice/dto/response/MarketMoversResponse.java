package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Value
@Builder
@Jacksonized
public class MarketMoversResponse {
    List<MoverEntryResponse> gainers;
    List<MoverEntryResponse> losers;
}
