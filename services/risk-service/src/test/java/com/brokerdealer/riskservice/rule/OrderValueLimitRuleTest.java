package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderValueLimitRuleTest {

    private final OrderValueLimitRule rule = new OrderValueLimitRule(new BigDecimal("500000"));

    private RiskEvaluationRequest requestWithValue(String orderValue) {
        RiskEvaluationRequest request = new RiskEvaluationRequest();
        request.setOrderId(UUID.randomUUID());
        request.setQuantity(100);
        request.setOrderValue(orderValue);
        return request;
    }

    @Test
    void shouldReturnPassedWhenValueBelowThreshold() {
        RiskResult result = rule.evaluate(requestWithValue("499999.00"));
        assertThat(result.getResult()).isEqualTo("PASSED");
        assertThat(result.getFailedRule()).isNull();
    }

    @Test
    void shouldReturnPassedWhenValueEqualsThreshold() {
        RiskResult result = rule.evaluate(requestWithValue("500000.00"));
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldReturnRejectedWhenValueExceedsThreshold() {
        RiskResult result = rule.evaluate(requestWithValue("600000.00"));
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("ORDER_VALUE_LIMIT");
        assertThat(result.getReason()).contains("600000").contains("500000");
    }

    @Test
    void shouldReturnPassedWhenOrderValueIsNull() {
        RiskEvaluationRequest request = new RiskEvaluationRequest();
        request.setOrderId(UUID.randomUUID());
        request.setQuantity(100);
        // orderValue is null

        RiskResult result = rule.evaluate(request);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldUseCorrectRuleCode() {
        assertThat(rule.getRuleCode()).isEqualTo("ORDER_VALUE_LIMIT");
    }
}
