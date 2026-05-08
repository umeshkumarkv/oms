package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@RequiredArgsConstructor
public class SingleOrderQtyLimitRule implements RiskRule {

    private final BigDecimal threshold;
    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return "SINGLE_ORDER_QTY_LIMIT";
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
                    "Qty " + request.getQuantity() + " exceeds limit of " + threshold.toPlainString());
        }
        return RiskResult.passed();
    }
}
