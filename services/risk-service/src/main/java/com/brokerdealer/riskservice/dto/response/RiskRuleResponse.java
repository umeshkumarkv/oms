package com.brokerdealer.riskservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RiskRuleResponse {
    String ruleCode;
    String ruleName;
    String ruleCategory;
    String description;
    Boolean enabled;
    String thresholdValue;
    String thresholdUnit;
    String action;
    Integer priority;
}
