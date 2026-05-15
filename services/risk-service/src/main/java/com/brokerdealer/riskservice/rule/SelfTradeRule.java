package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SelfTradeRule implements RiskRule {

    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return "RULE_SELF_TRADE";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public RiskResult evaluate(RiskEvaluationRequest request, RiskContext context) {
        if (Boolean.TRUE.equals(request.getHasCrossingOpenOrder())) {
            return RiskResult.rejected(getRuleCode(),
                    "SELF_TRADE_DETECTED: Order would cross with an existing open order on the opposite side"
                            + " [clientId=" + request.getClientId()
                            + " symbol=" + request.getSymbol()
                            + " side=" + request.getSide() + "]");
        }
        return RiskResult.passed();
    }
}
