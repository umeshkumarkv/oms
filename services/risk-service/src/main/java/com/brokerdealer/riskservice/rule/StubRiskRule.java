package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import lombok.RequiredArgsConstructor;

/**
 * Fallback rule for unknown or future rule codes in the DB.
 * Always returns PASSED to prevent blocking orders for unimplemented rules.
 */
@RequiredArgsConstructor
public class StubRiskRule implements RiskRule {

    private final String ruleCode;
    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return ruleCode;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public RiskResult evaluate(RiskEvaluationRequest request, RiskContext context) {
        return RiskResult.passed();
    }
}
