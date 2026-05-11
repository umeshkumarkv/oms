package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarginCallActiveRuleTest {

    private final MarginCallActiveRule rule = new MarginCallActiveRule(true);
    private final RiskContext context = RiskContext.builder()
            .orderId(UUID.randomUUID()).clientId(UUID.randomUUID()).build();

    private RiskEvaluationRequest requestWith(Boolean marginCallActive, String side) {
        RiskEvaluationRequest request = new RiskEvaluationRequest();
        request.setOrderId(UUID.randomUUID());
        request.setClientId(UUID.randomUUID());
        request.setSymbol("AAPL");
        request.setSide(side);
        request.setOrderType("LIMIT");
        request.setQuantity(100);
        request.setMarginCallActive(marginCallActive);
        return request;
    }

    // ─── AC: margin call active + BUY → REJECT ────────────────────────────────

    @Test
    void shouldRejectBuyOrderWhenMarginCallIsActive() {
        RiskResult result = rule.evaluate(requestWith(true, "BUY"), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("RULE_MARGIN_CALL_ACTIVE");
        assertThat(result.getReason()).containsIgnoringCase("margin call")
                .containsIgnoringCase("SELL");
    }

    // ─── AC: margin call active + SELL → PASS ─────────────────────────────────

    @Test
    void shouldPassSellOrderWhenMarginCallIsActive() {
        RiskResult result = rule.evaluate(requestWith(true, "SELL"), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
        assertThat(result.getFailedRule()).isNull();
    }

    // ─── AC: no margin call + BUY → PASS ──────────────────────────────────────

    @Test
    void shouldPassBuyOrderWhenNoMarginCall() {
        RiskResult result = rule.evaluate(requestWith(false, "BUY"), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
        assertThat(result.getFailedRule()).isNull();
    }

    // ─── AC: null marginCallActive → PASS (null guard) ────────────────────────

    @Test
    void shouldPassWhenMarginCallActiveIsNull() {
        RiskResult result = rule.evaluate(requestWith(null, "BUY"), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
        assertThat(result.getFailedRule()).isNull();
    }

    // ─── AC: rule disabled → isEnabled returns false ──────────────────────────

    @Test
    void shouldReflectDisabledStateWhenConstructedDisabled() {
        MarginCallActiveRule disabled = new MarginCallActiveRule(false);
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(rule.isEnabled()).isTrue();
    }

    // ─── AC: rule code is correct ─────────────────────────────────────────────

    @Test
    void shouldReturnCorrectRuleCode() {
        assertThat(rule.getRuleCode()).isEqualTo("RULE_MARGIN_CALL_ACTIVE");
    }
}
