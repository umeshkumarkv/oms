package com.brokerdealer.riskservice.domain;

import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;

public interface RiskRule {

    String getRuleCode();

    boolean isEnabled();

    RiskResult evaluate(RiskEvaluationRequest request, RiskContext context);
}
