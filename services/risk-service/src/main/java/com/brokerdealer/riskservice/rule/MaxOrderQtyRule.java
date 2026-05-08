package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@RequiredArgsConstructor
public class MaxOrderQtyRule implements RiskRule {

    private final BigDecimal threshold;
    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return "RULE_MAX_ORDER_QTY";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public RiskResult evaluate(RiskEvaluationRequest request, RiskContext context) {
        BigDecimal qty = BigDecimal.valueOf(request.getQuantity());
        if (qty.compareTo(threshold) > 0) {
            return RiskResult.rejected(getRuleCode(),
                    "Order quantity " + request.getQuantity() + " exceeds maximum allowed "
                            + threshold.toBigInteger() + " shares");
        }
        return RiskResult.passed();
    }
}
