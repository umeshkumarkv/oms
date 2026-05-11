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
}
