package com.brokerdealer.orderservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class StatusCountsResponse {
    Map<String, Integer> data;
    ResponseMeta meta;
}
