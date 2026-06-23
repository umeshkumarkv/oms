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
    private UUID userId;              // auth-service userId — passed through to risk result for WS push
    private String symbol;
    private String side;             // "BUY" | "SELL"
    private String orderType;        // "MARKET" | "LIMIT"
    private Integer quantity;
    private String limitPrice;       // BigDecimal as String, null for MARKET orders
    private String timeInForce;      // "DAY" | "GTC"
    private String orderValue;       // BigDecimal as String: limitPrice*qty or lastPrice*qty
    private Boolean marginCallActive;

    // --- Portfolio snapshot fields (SCRUM-119) ---
    private String buyingPower;          // BigDecimal as String — cashBalance minus in-flight BUY commitments
    private Integer holdingsForSymbol;   // quantity held for this symbol, 0 if none
    private String kycStatus;            // "VERIFIED" | "PENDING" | "EXPIRED"
    private String kycExpiryDate;        // ISO 8601 date string e.g. "2027-01-01"
    private Boolean tradingEnabled;
    private String lastPrice;            // BigDecimal as String — market last price at submission
    private Boolean hasCrossingOpenOrder; // true if open opposite order at crossing price exists
    private Integer dailyOrderCount;     // number of orders submitted today by this client
    private Integer maxDailyOrders;      // per-client max daily order count (from portfolio)
    private String avgOrderQty;          // BigDecimal as String — AVG(quantity) last 90 days
    private String avgOrderValue;        // BigDecimal as String — AVG(orderValue) last 90 days
}
