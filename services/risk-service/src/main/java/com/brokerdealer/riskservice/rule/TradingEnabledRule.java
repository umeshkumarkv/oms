package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TradingEnabledRule implements RiskRule {

    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return "RULE_TRADING_ENABLED";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public RiskResult evaluate(RiskEvaluationRequest request, RiskContext context) {
        if (Boolean.FALSE.equals(request.getTradingEnabled())) {
            return RiskResult.rejected(getRuleCode(),
                    "TRADING_DISABLED: Trading is disabled for this account");
        }
        return RiskResult.passed();
    }
}
