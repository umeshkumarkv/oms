package com.brokerdealer.riskservice.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class RiskEvaluationRequest {

    private UUID orderId;
    private UUID clientId;
    private UUID userId;
    private String symbol;
    private String side;
    private String orderType;
    private Integer quantity;
    private String limitPrice;
    private String timeInForce;
    private String orderValue;
    private Boolean marginCallActive;

    // --- Portfolio snapshot fields (SCRUM-119/120) ---
    private String buyingPower;          // BigDecimal as String — cashBalance - reservedCash
    private Integer holdingsForSymbol;   // quantity held for this symbol, 0 if none
    private String kycStatus;            // "VERIFIED" | "PENDING" | "EXPIRED"
    private String kycExpiryDate;        // ISO 8601 date string e.g. "2027-01-01"
    private Boolean tradingEnabled;

    // --- Stateful check fields (SCRUM-121) ---
    private Boolean hasCrossingOpenOrder; // true if an open opposite-side order at crossing price exists

    // --- Fat-finger / WARN rule fields (SCRUM-122) ---
    private String  lastPrice;       // BigDecimal as String; null for MARKET orders
    private String  avgOrderQty;     // BigDecimal as String; "0" if no history — triggers skip in ORDER_QTY_SPIKE_CHECK
    private String  avgOrderValue;   // BigDecimal as String; null if no history
    private Integer dailyOrderCount; // orders placed by this client today
    private Integer maxDailyOrders;  // per-client daily limit
}
