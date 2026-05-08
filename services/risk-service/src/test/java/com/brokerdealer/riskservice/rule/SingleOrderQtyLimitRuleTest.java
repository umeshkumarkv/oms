package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SingleOrderQtyLimitRuleTest {

    private final SingleOrderQtyLimitRule rule = new SingleOrderQtyLimitRule(new BigDecimal("10000"), true);
    private final RiskContext context = RiskContext.builder().orderId(UUID.randomUUID()).clientId(UUID.randomUUID()).build();

    private RiskEvaluationRequest requestWithQty(int qty) {
        RiskEvaluationRequest request = new RiskEvaluationRequest();
        request.setOrderId(UUID.randomUUID());
        request.setQuantity(qty);
        return request;
    }

    @Test
    void shouldReturnPassedWhenQtyBelowThreshold() {
        RiskResult result = rule.evaluate(requestWithQty(9999), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
        assertThat(result.getFailedRule()).isNull();
    }

    @Test
    void shouldReturnPassedWhenQtyEqualsThreshold() {
        RiskResult result = rule.evaluate(requestWithQty(10000), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldReturnRejectedWhenQtyExceedsThreshold() {
        RiskResult result = rule.evaluate(requestWithQty(15000), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("SINGLE_ORDER_QTY_LIMIT");
        assertThat(result.getReason()).contains("15000").contains("10000");
    }

    @Test
    void shouldUseCorrectRuleCode() {
        assertThat(rule.getRuleCode()).isEqualTo("SINGLE_ORDER_QTY_LIMIT");
    }

    @Test
    void shouldReflectEnabledFlagFromConstructor() {
        SingleOrderQtyLimitRule disabled = new SingleOrderQtyLimitRule(new BigDecimal("10000"), false);
        assertThat(rule.isEnabled()).isTrue();
        assertThat(disabled.isEnabled()).isFalse();
    }
}
