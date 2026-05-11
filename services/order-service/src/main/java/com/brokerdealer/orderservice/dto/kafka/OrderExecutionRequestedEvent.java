package com.brokerdealer.orderservice.dto.kafka;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Value
@Builder
public class OrderExecutionRequestedEvent {
    UUID orderId;
    UUID clientId;
    String symbol;
    String side;
    String orderType;
    Integer quantity;
    BigDecimal limitPrice;
    String timeInForce;
    BigDecimal orderValue;
    BigDecimal lastPrice;
}
