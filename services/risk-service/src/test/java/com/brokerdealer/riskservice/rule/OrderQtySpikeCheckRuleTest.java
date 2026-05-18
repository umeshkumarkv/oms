package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderQtySpikeCheckRuleTest {

    private static final BigDecimal MULTIPLIER = new BigDecimal("10");

    private final RiskContext context = RiskContext.builder()
            .orderId(UUID.randomUUID())
            .clientId(UUID.randomUUID())
            .build();

    private RiskEvaluationRequest request(Integer quantity, String avgOrderQty) {
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setQuantity(quantity);
        req.setAvgOrderQty(avgOrderQty);
        return req;
    }

    @Test
    void shouldWarnWhenQuantityExceedsTenTimesAvg() {
        // qty=1500, avg=100, spike=1000 → 1500 > 1000 → WARNED
        OrderQtySpikeCheckRule rule = new OrderQtySpikeCheckRule(MULTIPLIER, true);
        RiskResult result = rule.evaluate(request(1500, "100"), context);

        assertThat(result.getResult()).isEqualTo("WARNED");
        assertThat(result.getFailedRule()).isEqualTo("ORDER_QTY_SPIKE_CHECK");
        assertThat(result.getReason()).contains("QTY_SPIKE");
    }

    @Test
    void shouldPassWhenQuantityBelowSpike() {
        // qty=500, avg=100, spike=1000 → 500 < 1000 → PASSED
        OrderQtySpikeCheckRule rule = new OrderQtySpikeCheckRule(MULTIPLIER, true);
        RiskResult result = rule.evaluate(request(500, "100"), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldSkipWhenAvgOrderQtyIsZero() {
        OrderQtySpikeCheckRule rule = new OrderQtySpikeCheckRule(MULTIPLIER, true);
        RiskResult result = rule.evaluate(request(5000, "0"), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldSkipWhenAvgOrderQtyIsNull() {
        OrderQtySpikeCheckRule rule = new OrderQtySpikeCheckRule(MULTIPLIER, true);
        RiskResult result = rule.evaluate(request(5000, null), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldSkipWhenQuantityIsNull() {
        OrderQtySpikeCheckRule rule = new OrderQtySpikeCheckRule(MULTIPLIER, true);
        RiskResult result = rule.evaluate(request(null, "100"), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldReturnCorrectRuleCode() {
        OrderQtySpikeCheckRule rule = new OrderQtySpikeCheckRule(MULTIPLIER, true);
        assertThat(rule.getRuleCode()).isEqualTo("ORDER_QTY_SPIKE_CHECK");
    }

    @Test
    void shouldReportDisabledCorrectly() {
        OrderQtySpikeCheckRule rule = new OrderQtySpikeCheckRule(MULTIPLIER, false);
        assertThat(rule.isEnabled()).isFalse();
    }
}
