package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SingleOrderQtyLimitRuleTest {

    private final SingleOrderQtyLimitRule rule = new SingleOrderQtyLimitRule(new BigDecimal("10000"));

    private RiskEvaluationRequest requestWithQty(int qty) {
        RiskEvaluationRequest request = new RiskEvaluationRequest();
        request.setOrderId(UUID.randomUUID());
        request.setQuantity(qty);
        return request;
    }

    @Test
    void shouldReturnPassedWhenQtyBelowThreshold() {
        RiskResult result = rule.evaluate(requestWithQty(9999));
        assertThat(result.getResult()).isEqualTo("PASSED");
        assertThat(result.getFailedRule()).isNull();
    }

    @Test
    void shouldReturnPassedWhenQtyEqualsThreshold() {
        RiskResult result = rule.evaluate(requestWithQty(10000));
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldReturnRejectedWhenQtyExceedsThreshold() {
        RiskResult result = rule.evaluate(requestWithQty(15000));
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("SINGLE_ORDER_QTY_LIMIT");
        assertThat(result.getReason()).contains("15000").contains("10000");
    }

    @Test
    void shouldUseCorrectRuleCode() {
        assertThat(rule.getRuleCode()).isEqualTo("SINGLE_ORDER_QTY_LIMIT");
    }
}
