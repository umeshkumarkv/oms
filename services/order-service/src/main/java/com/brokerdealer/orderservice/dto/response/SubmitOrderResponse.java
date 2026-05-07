package com.brokerdealer.orderservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SubmitOrderResponse {
    SubmitOrderData data;
    ResponseMeta meta;
}
