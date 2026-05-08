package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MaxOrderQtyRuleTest {

    private final MaxOrderQtyRule rule = new MaxOrderQtyRule(new BigDecimal("50000"), true);
    private final RiskContext context = RiskContext.builder()
            .orderId(UUID.randomUUID()).clientId(UUID.randomUUID()).build();

    private RiskEvaluationRequest requestWithQty(int qty) {
        RiskEvaluationRequest request = new RiskEvaluationRequest();
        request.setOrderId(UUID.randomUUID());
        request.setQuantity(qty);
        return request;
    }

    // ─── AC: quantity over threshold → REJECT ─────────────────────────────────

    @Test
    void shouldReturnRejectedWhenQtyExceedsThreshold() {
        RiskResult result = rule.evaluate(requestWithQty(50001), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("RULE_MAX_ORDER_QTY");
        assertThat(result.getReason()).contains("50001").contains("50000");
    }

    // ─── AC: quantity at threshold → PASS (boundary) ──────────────────────────

    @Test
    void shouldReturnPassedWhenQtyEqualsThreshold() {
        RiskResult result = rule.evaluate(requestWithQty(50000), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
        assertThat(result.getFailedRule()).isNull();
    }

    // ─── AC: quantity well below threshold → PASS ─────────────────────────────

    @Test
    void shouldReturnPassedWhenQtyIsWellBelowThreshold() {
        RiskResult result = rule.evaluate(requestWithQty(100), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    // ─── AC: rule disabled → isEnabled returns false ──────────────────────────

    @Test
    void shouldReflectDisabledStateWhenConstructedDisabled() {
        MaxOrderQtyRule disabled = new MaxOrderQtyRule(new BigDecimal("50000"), false);
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(rule.isEnabled()).isTrue();
    }

    // ─── AC: rule code is correct ─────────────────────────────────────────────

    @Test
    void shouldReturnCorrectRuleCode() {
        assertThat(rule.getRuleCode()).isEqualTo("RULE_MAX_ORDER_QTY");
    }

    // ─── Rejection message includes human-readable reason ─────────────────────

    @Test
    void shouldIncludeHumanReadableReasonInRejection() {
        RiskResult result = rule.evaluate(requestWithQty(75000), context);
        assertThat(result.getReason())
                .contains("75000")
                .contains("50000")
                .containsIgnoringCase("shares");
    }
}
