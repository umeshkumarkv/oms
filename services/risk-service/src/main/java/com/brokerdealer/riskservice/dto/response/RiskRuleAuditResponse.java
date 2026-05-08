package com.brokerdealer.riskservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class RiskRuleAuditResponse {
    UUID id;
    String ruleCode;
    String fieldChanged;
    String oldValue;
    String newValue;
    String changedBy;
    String changeReason;
    Instant changedAt;
}
