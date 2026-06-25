package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderValueLimitRuleTest {

    private final OrderValueLimitRule rule = new OrderValueLimitRule(new BigDecimal("500000"), true);
    private final RiskContext context = RiskContext.builder().orderId(UUID.randomUUID()).clientId(UUID.randomUUID()).build();

    private RiskEvaluationRequest requestWithValue(String orderValue) {
        RiskEvaluationRequest request = new RiskEvaluationRequest();
        request.setOrderId(UUID.randomUUID());
        request.setQuantity(100);
        request.setOrderValue(orderValue);
        return request;
    }

    @Test
    void shouldReturnPassedWhenValueBelowThreshold() {
        RiskResult result = rule.evaluate(requestWithValue("499999.00"), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
        assertThat(result.getFailedRule()).isNull();
    }

    @Test
    void shouldReturnPassedWhenValueEqualsThreshold() {
        RiskResult result = rule.evaluate(requestWithValue("500000.00"), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldReturnRejectedWhenValueExceedsThreshold() {
        RiskResult result = rule.evaluate(requestWithValue("600000.00"), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("ORDER_VALUE_LIMIT");
        assertThat(result.getReason()).isEqualTo("Order value limit exceeded — ORDER_VALUE_LIMIT");
    }

    @Test
    void shouldReturnPassedWhenOrderValueIsNull() {
        RiskEvaluationRequest request = new RiskEvaluationRequest();
        request.setOrderId(UUID.randomUUID());
        request.setQuantity(100);

        RiskResult result = rule.evaluate(request, context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldUseCorrectRuleCode() {
        assertThat(rule.getRuleCode()).isEqualTo("ORDER_VALUE_LIMIT");
    }

    @Test
    void shouldReflectEnabledFlagFromConstructor() {
        OrderValueLimitRule disabled = new OrderValueLimitRule(new BigDecimal("500000"), false);
        assertThat(rule.isEnabled()).isTrue();
        assertThat(disabled.isEnabled()).isFalse();
    }
}
