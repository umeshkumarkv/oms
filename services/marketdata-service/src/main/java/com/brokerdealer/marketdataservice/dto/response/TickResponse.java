package com.brokerdealer.marketdataservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TickResponse {
    String time;    // ISO-8601 local ET: "2026-05-08T14:31"
    String price;
    String bid;
    String ask;
    Long   volume;
}
