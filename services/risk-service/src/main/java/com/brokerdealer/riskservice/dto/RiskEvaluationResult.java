package com.brokerdealer.riskservice.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class RiskEvaluationResult {

    UUID orderId;
    String result;
    String ruleCode;
    String reason;
    String timestamp;
}
