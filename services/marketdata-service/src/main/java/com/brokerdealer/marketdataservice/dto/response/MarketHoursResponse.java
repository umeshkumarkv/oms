package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MarketHoursResponse {
    Boolean marketOpen;
    String openTime;
    String closeTime;
    String timezone;
    String nextOpenDate;
}
