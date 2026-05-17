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
public class OrderQtySpikeCheckRule implements RiskRule {

    private final BigDecimal multiplier;
    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return "ORDER_QTY_SPIKE_CHECK";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public RiskResult evaluate(RiskEvaluationRequest request, RiskContext context) {
        if (request.getQuantity() == null || request.getAvgOrderQty() == null || multiplier == null) {
            log.debug("ORDER_QTY_SPIKE_CHECK skipped: required field is null; orderId={}", context.getOrderId());
            return RiskResult.passed();
        }
        if (request.getAvgOrderQty() == 0) {
            log.debug("ORDER_QTY_SPIKE_CHECK skipped: avgOrderQty is 0 (no history); orderId={}", context.getOrderId());
            return RiskResult.passed();
        }

        BigDecimal qty    = new BigDecimal(request.getQuantity().toString());
        BigDecimal avgQty = new BigDecimal(request.getAvgOrderQty().toString());
        BigDecimal spike  = avgQty.multiply(multiplier);

        if (qty.compareTo(spike) > 0) {
            log.warn("ORDER_QTY_SPIKE_CHECK triggered: orderId={} qty={} spike={}", context.getOrderId(), qty, spike);
            return RiskResult.warned(getRuleCode(),
                    "QTY_SPIKE: quantity " + qty.toPlainString() + " exceeds " + multiplier.toPlainString()
                    + "x avgOrderQty (" + avgQty.toPlainString() + ")");
        }
        return RiskResult.passed();
    }
}
