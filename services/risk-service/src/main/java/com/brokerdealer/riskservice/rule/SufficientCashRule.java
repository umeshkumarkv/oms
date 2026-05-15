package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@RequiredArgsConstructor
public class SufficientCashRule implements RiskRule {

    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return "RULE_SUFFICIENT_CASH";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public RiskResult evaluate(RiskEvaluationRequest request, RiskContext context) {
        if (!"BUY".equals(request.getSide())) {
            return RiskResult.passed();
        }
        if (request.getBuyingPower() == null) {
            return RiskResult.passed();
        }
        BigDecimal buyingPower = new BigDecimal(request.getBuyingPower());
        BigDecimal orderValue = new BigDecimal(request.getOrderValue());
        if (buyingPower.compareTo(orderValue) < 0) {
            return RiskResult.rejected(getRuleCode(),
                    "INSUFFICIENT_BUYING_POWER: Buying power " + buyingPower.toPlainString()
                            + " is less than order value " + orderValue.toPlainString());
        }
        return RiskResult.passed();
    }
}
