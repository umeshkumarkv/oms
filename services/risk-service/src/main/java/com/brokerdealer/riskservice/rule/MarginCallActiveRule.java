package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MarginCallActiveRule implements RiskRule {

    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return "RULE_MARGIN_CALL_ACTIVE";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public RiskResult evaluate(RiskEvaluationRequest request, RiskContext context) {
        if (request.getMarginCallActive() == null || !request.getMarginCallActive()) {
            return RiskResult.passed();
        }
        if ("BUY".equals(request.getSide())) {
            return RiskResult.rejected(getRuleCode(),
                    "Account has an active margin call. Only SELL orders are permitted until the margin call is resolved.");
        }
        return RiskResult.passed();
    }
}
