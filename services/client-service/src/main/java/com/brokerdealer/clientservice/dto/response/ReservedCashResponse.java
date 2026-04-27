package com.brokerdealer.clientservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class ReservedCashResponse {

    UUID clientId;
    String reservedCash;
    String buyingPower;
}
