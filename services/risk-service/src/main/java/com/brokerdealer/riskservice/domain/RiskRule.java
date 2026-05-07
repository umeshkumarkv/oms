package com.brokerdealer.riskservice.domain;

import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;

public interface RiskRule {

    String getRuleCode();

    RiskResult evaluate(RiskEvaluationRequest request);
}
