package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@RequiredArgsConstructor
public class OrderValueLimitRule implements RiskRule {

    private final BigDecimal threshold;
    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return "ORDER_VALUE_LIMIT";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public RiskResult evaluate(RiskEvaluationRequest request, RiskContext context) {
        if (request.getOrderValue() == null) {
            return RiskResult.passed();
        }
        BigDecimal orderValue = new BigDecimal(request.getOrderValue());
        if (orderValue.compareTo(threshold) > 0) {
            return RiskResult.rejected(getRuleCode(),
                    "Order value limit exceeded — " + getRuleCode());
        }
        return RiskResult.passed();
    }
}
