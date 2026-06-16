package com.brokerdealer.clientservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class ActiveClientResponse {
    UUID clientId;
    String clientName;
    String turnoverToday;
    String availableCash;
    Boolean cashLimitWarning;
}
