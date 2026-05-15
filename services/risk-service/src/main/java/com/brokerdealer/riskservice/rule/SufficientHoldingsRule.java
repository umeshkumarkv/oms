package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SufficientHoldingsRule implements RiskRule {

    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return "RULE_SUFFICIENT_HOLDINGS";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public RiskResult evaluate(RiskEvaluationRequest request, RiskContext context) {
        if (!"SELL".equals(request.getSide())) {
            return RiskResult.passed();
        }
        int holdings = request.getHoldingsForSymbol() != null ? request.getHoldingsForSymbol() : 0;
        if (holdings < request.getQuantity()) {
            return RiskResult.rejected(getRuleCode(),
                    "INSUFFICIENT_HOLDINGS: Holdings " + holdings
                            + " is less than order quantity " + request.getQuantity());
        }
        return RiskResult.passed();
    }
}
