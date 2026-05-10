package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MaxOrderValueRuleTest {

    private static final BigDecimal THRESHOLD = new BigDecimal("500000");
    private final MaxOrderValueRule rule = new MaxOrderValueRule(THRESHOLD, true);
    private final RiskContext context = RiskContext.builder()
            .orderId(UUID.randomUUID()).clientId(UUID.randomUUID()).build();

    private RiskEvaluationRequest requestWithOrderValue(String orderValue, String orderType) {
        RiskEvaluationRequest request = new RiskEvaluationRequest();
        request.setOrderId(UUID.randomUUID());
        request.setQuantity(1);
        request.setOrderType(orderType);
        request.setOrderValue(orderValue);
        return request;
    }

    // ─── AC: LIMIT order over threshold → REJECT ──────────────────────────────

    @Test
    void shouldReturnRejectedWhenLimitOrderValueExceedsThreshold() {
        RiskResult result = rule.evaluate(requestWithOrderValue("500001.00", "LIMIT"), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("RULE_MAX_ORDER_VALUE");
        assertThat(result.getReason()).contains("500001").contains("500000").containsIgnoringCase("USD");
    }

    // ─── AC: MARKET order over threshold → REJECT ─────────────────────────────

    @Test
    void shouldReturnRejectedWhenMarketOrderValueExceedsThreshold() {
        RiskResult result = rule.evaluate(requestWithOrderValue("500001.00", "MARKET"), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("RULE_MAX_ORDER_VALUE");
        assertThat(result.getReason()).contains("500001").contains("500000");
    }

    // ─── AC: order value exactly at threshold → PASS (boundary) ───────────────

    @Test
    void shouldReturnPassedWhenOrderValueEqualsThreshold() {
        RiskResult result = rule.evaluate(requestWithOrderValue("500000.00", "LIMIT"), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
        assertThat(result.getFailedRule()).isNull();
    }

    // ─── AC: order value well below threshold → PASS ──────────────────────────

    @Test
    void shouldReturnPassedWhenOrderValueIsWellBelowThreshold() {
        // 100 AAPL @ $175.50 = $17,550
        RiskResult result = rule.evaluate(requestWithOrderValue("17550.00", "LIMIT"), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    // ─── AC: null orderValue → PASS (defensive guard) ─────────────────────────

    @Test
    void shouldReturnPassedWhenOrderValueIsNull() {
        RiskEvaluationRequest request = new RiskEvaluationRequest();
        request.setOrderId(UUID.randomUUID());
        request.setQuantity(100);
        request.setOrderType("LIMIT");
        // orderValue intentionally not set
        RiskResult result = rule.evaluate(request, context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    // ─── AC: rule disabled → isEnabled returns false ──────────────────────────

    @Test
    void shouldReflectDisabledStateWhenConstructedDisabled() {
        MaxOrderValueRule disabled = new MaxOrderValueRule(THRESHOLD, false);
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(rule.isEnabled()).isTrue();
    }

    // ─── AC: rule code is correct ─────────────────────────────────────────────

    @Test
    void shouldReturnCorrectRuleCode() {
        assertThat(rule.getRuleCode()).isEqualTo("RULE_MAX_ORDER_VALUE");
    }
}
