package com.brokerdealer.riskservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class RiskRuleResponse {
    String ruleCode;
    String ruleName;
    String ruleCategory;
    String description;
    Boolean enabled;
    BigDecimal thresholdValue;
    String thresholdUnit;
    String action;
    Integer priority;
}
