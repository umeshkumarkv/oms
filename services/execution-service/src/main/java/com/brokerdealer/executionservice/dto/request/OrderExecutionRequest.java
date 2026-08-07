package com.brokerdealer.executionservice.dto.request;

import com.brokerdealer.executionservice.domain.OrderSide;
import com.brokerdealer.executionservice.domain.OrderType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
public class OrderExecutionRequest {
    private UUID orderId;
    private UUID clientId;
    private String symbol;
    private OrderSide side;
    private OrderType orderType;
    private Integer quantity;
    private BigDecimal limitPrice;
    private String timeInForce;
    private BigDecimal orderValue;
    private BigDecimal lastPrice;
    private Boolean fillOrKill;
}
