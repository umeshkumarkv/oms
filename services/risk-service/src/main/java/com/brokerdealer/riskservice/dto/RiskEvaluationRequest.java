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
}
