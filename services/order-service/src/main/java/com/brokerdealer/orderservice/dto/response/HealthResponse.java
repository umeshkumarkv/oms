package com.brokerdealer.orderservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class HealthResponse {
    List<ServiceHealthEntry> data;
    ResponseMeta meta;
}
