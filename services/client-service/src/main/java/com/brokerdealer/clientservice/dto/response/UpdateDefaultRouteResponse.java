package com.brokerdealer.clientservice.dto.response;

import com.brokerdealer.clientservice.domain.DefaultRoute;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class UpdateDefaultRouteResponse {
    UUID clientId;
    DefaultRoute defaultRoute;
}
