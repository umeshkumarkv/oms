package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderValueSpikeCheckRuleTest {

    private static final BigDecimal MULTIPLIER = new BigDecimal("10");

    private final RiskContext context = RiskContext.builder()
            .orderId(UUID.randomUUID())
            .clientId(UUID.randomUUID())
            .build();

    private RiskEvaluationRequest request(String orderValue, String avgOrderValue) {
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setOrderValue(orderValue);
        req.setAvgOrderValue(avgOrderValue);
        return req;
    }

    @Test
    void shouldWarnWhenOrderValueExceedsTenTimesAvgOrderValue() {
        // orderValue=600000, avg=50000, spike=500000 → 600000 > 500000 → WARNED
        OrderValueSpikeCheckRule rule = new OrderValueSpikeCheckRule(MULTIPLIER, true);
        RiskResult result = rule.evaluate(request("600000.00", "50000.00"), context);

        assertThat(result.getResult()).isEqualTo("WARNED");
        assertThat(result.getFailedRule()).isEqualTo("ORDER_VALUE_SPIKE_CHECK");
        assertThat(result.getReason()).contains("VALUE_SPIKE");
    }

    @Test
    void shouldPassWhenOrderValueBelowSpike() {
        // orderValue=200000, avg=50000, spike=500000 → 200000 < 500000 → PASSED
        OrderValueSpikeCheckRule rule = new OrderValueSpikeCheckRule(MULTIPLIER, true);
        RiskResult result = rule.evaluate(request("200000.00", "50000.00"), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldSkipWhenOrderValueIsNull() {
        OrderValueSpikeCheckRule rule = new OrderValueSpikeCheckRule(MULTIPLIER, true);
        RiskResult result = rule.evaluate(request(null, "50000.00"), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldSkipWhenAvgOrderValueIsNull() {
        OrderValueSpikeCheckRule rule = new OrderValueSpikeCheckRule(MULTIPLIER, true);
        RiskResult result = rule.evaluate(request("600000.00", null), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldReturnCorrectRuleCode() {
        OrderValueSpikeCheckRule rule = new OrderValueSpikeCheckRule(MULTIPLIER, true);
        assertThat(rule.getRuleCode()).isEqualTo("ORDER_VALUE_SPIKE_CHECK");
    }

    @Test
    void shouldReportDisabledCorrectly() {
        OrderValueSpikeCheckRule rule = new OrderValueSpikeCheckRule(MULTIPLIER, false);
        assertThat(rule.isEnabled()).isFalse();
    }
}
