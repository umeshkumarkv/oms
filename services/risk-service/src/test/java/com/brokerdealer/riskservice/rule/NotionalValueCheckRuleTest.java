package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotionalValueCheckRuleTest {

    private static final BigDecimal THRESHOLD = new BigDecimal("1000000");

    private final RiskContext context = RiskContext.builder()
            .orderId(UUID.randomUUID())
            .clientId(UUID.randomUUID())
            .build();

    private RiskEvaluationRequest requestWithOrderValue(String orderValue) {
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setOrderValue(orderValue);
        return req;
    }

    @Test
    void shouldWarnWhenOrderValueExceedsThreshold() {
        NotionalValueCheckRule rule = new NotionalValueCheckRule(THRESHOLD, true);
        RiskResult result = rule.evaluate(requestWithOrderValue("1500000.00"), context);

        assertThat(result.getResult()).isEqualTo("WARNED");
        assertThat(result.getFailedRule()).isEqualTo("NOTIONAL_VALUE_CHECK");
        assertThat(result.getReason()).contains("NOTIONAL_VALUE");
    }

    @Test
    void shouldPassWhenOrderValueBelowThreshold() {
        NotionalValueCheckRule rule = new NotionalValueCheckRule(THRESHOLD, true);
        RiskResult result = rule.evaluate(requestWithOrderValue("500000.00"), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldPassWhenOrderValueEqualsThreshold() {
        NotionalValueCheckRule rule = new NotionalValueCheckRule(THRESHOLD, true);
        RiskResult result = rule.evaluate(requestWithOrderValue("1000000.00"), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldSkipWhenOrderValueIsNull() {
        NotionalValueCheckRule rule = new NotionalValueCheckRule(THRESHOLD, true);
        RiskResult result = rule.evaluate(requestWithOrderValue(null), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldReturnCorrectRuleCode() {
        NotionalValueCheckRule rule = new NotionalValueCheckRule(THRESHOLD, true);
        assertThat(rule.getRuleCode()).isEqualTo("NOTIONAL_VALUE_CHECK");
    }

    @Test
    void shouldReportDisabledCorrectly() {
        NotionalValueCheckRule rule = new NotionalValueCheckRule(THRESHOLD, false);
        assertThat(rule.isEnabled()).isFalse();
    }
}
