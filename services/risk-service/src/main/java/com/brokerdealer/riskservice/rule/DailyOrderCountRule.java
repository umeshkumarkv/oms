package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class DailyOrderCountRule implements RiskRule {

    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return "RULE_DAILY_ORDER_COUNT";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public RiskResult evaluate(RiskEvaluationRequest request, RiskContext context) {
        if (request.getDailyOrderCount() == null || request.getMaxDailyOrders() == null) {
            log.debug("RULE_DAILY_ORDER_COUNT skipped: required field is null; orderId={}", context.getOrderId());
            return RiskResult.passed();
        }

        if (request.getDailyOrderCount() >= request.getMaxDailyOrders()) {
            log.warn("RULE_DAILY_ORDER_COUNT triggered: orderId={} dailyOrderCount={} maxDailyOrders={}",
                    context.getOrderId(), request.getDailyOrderCount(), request.getMaxDailyOrders());
            return RiskResult.warned(getRuleCode(),
                    "DAILY_ORDER_COUNT: dailyOrderCount " + request.getDailyOrderCount()
                    + " has reached maxDailyOrders limit of " + request.getMaxDailyOrders());
        }
        return RiskResult.passed();
    }
}
