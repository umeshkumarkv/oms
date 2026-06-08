package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class MarketMoversResponse {
    List<MoverEntryResponse> gainers;
    List<MoverEntryResponse> losers;
}
