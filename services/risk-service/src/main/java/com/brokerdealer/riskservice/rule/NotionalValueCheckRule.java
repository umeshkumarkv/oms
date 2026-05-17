package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Slf4j
public class NotionalValueCheckRule implements RiskRule {

    private final BigDecimal threshold;
    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return "NOTIONAL_VALUE_CHECK";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public RiskResult evaluate(RiskEvaluationRequest request, RiskContext context) {
        if (request.getOrderValue() == null || threshold == null) {
            log.debug("NOTIONAL_VALUE_CHECK skipped: orderValue or threshold is null; orderId={}", context.getOrderId());
            return RiskResult.passed();
        }

        BigDecimal orderValue = new BigDecimal(request.getOrderValue());

        if (orderValue.compareTo(threshold) > 0) {
            log.warn("NOTIONAL_VALUE_CHECK triggered: orderId={}", context.getOrderId());
            return RiskResult.warned(getRuleCode(),
                    "NOTIONAL_VALUE: orderValue exceeds threshold of " + threshold.toPlainString());
        }
        return RiskResult.passed();
    }
}
