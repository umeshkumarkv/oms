package com.brokerdealer.orderservice.dto.client;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReserveCashRequest {
    String adjustment;
    String reason;
}
