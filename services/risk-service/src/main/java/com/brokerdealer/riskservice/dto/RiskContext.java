package com.brokerdealer.riskservice.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class RiskContext {

    UUID orderId;
    UUID clientId;
}
