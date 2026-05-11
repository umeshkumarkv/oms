package com.brokerdealer.orderservice.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskEvaluationRequestedEvent {

    private UUID orderId;
    private UUID clientId;
    private UUID userId;         // auth-service userId — passed through to risk result for WS push
    private String symbol;
    private String side;        // "BUY" | "SELL"
    private String orderType;   // "MARKET" | "LIMIT"
    private Integer quantity;
    private String limitPrice;  // BigDecimal as String, null for MARKET orders
    private String timeInForce; // "DAY" | "GTC"
    private String orderValue;  // BigDecimal as String: limitPrice*qty or lastPrice*qty
    private Boolean marginCallActive;
}
