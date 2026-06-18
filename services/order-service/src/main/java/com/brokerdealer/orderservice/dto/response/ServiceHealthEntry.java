package com.brokerdealer.orderservice.dto.response;

import com.brokerdealer.orderservice.domain.ServiceHealthStatus;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ServiceHealthEntry {
    String service;
    ServiceHealthStatus status;
}
