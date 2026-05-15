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
public class OrderValueSpikeCheckRule implements RiskRule {

    private final BigDecimal multiplier;
    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return "ORDER_VALUE_SPIKE_CHECK";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public RiskResult evaluate(RiskEvaluationRequest request, RiskContext context) {
        if (request.getOrderValue() == null || request.getAvgOrderValue() == null || multiplier == null) {
            log.debug("ORDER_VALUE_SPIKE_CHECK skipped: required field is null; orderId={}", context.getOrderId());
            return RiskResult.passed();
        }

        BigDecimal orderValue    = new BigDecimal(request.getOrderValue());
        BigDecimal avgOrderValue = new BigDecimal(request.getAvgOrderValue());

        if (avgOrderValue.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("ORDER_VALUE_SPIKE_CHECK skipped: avgOrderValue is zero (no history); orderId={}", context.getOrderId());
            return RiskResult.passed();
        }

        BigDecimal spike = avgOrderValue.multiply(multiplier);

        if (orderValue.compareTo(spike) > 0) {
            log.warn("ORDER_VALUE_SPIKE_CHECK triggered: orderId={}", context.getOrderId());
            return RiskResult.warned(getRuleCode(),
                    "VALUE_SPIKE: orderValue " + orderValue.toPlainString() + " exceeds " + multiplier.toPlainString()
                    + "x avgOrderValue (" + avgOrderValue.toPlainString() + ")");
        }
        return RiskResult.passed();
    }
}
