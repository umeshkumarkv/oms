package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TradingEnabledRuleTest {

    private final RiskContext context = RiskContext.builder()
            .orderId(UUID.randomUUID())
            .clientId(UUID.randomUUID())
            .build();

    private RiskEvaluationRequest requestWith(Boolean tradingEnabled) {
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setTradingEnabled(tradingEnabled);
        req.setSide("BUY");
        return req;
    }

    @Test
    void shouldPassWhenTradingEnabled() {
        TradingEnabledRule rule = new TradingEnabledRule(true);
        RiskResult result = rule.evaluate(requestWith(true), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldRejectWhenTradingDisabled() {
        TradingEnabledRule rule = new TradingEnabledRule(true);
        RiskResult result = rule.evaluate(requestWith(false), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("RULE_TRADING_ENABLED");
        assertThat(result.getReason()).contains("TRADING_DISABLED");
    }

    @Test
    void shouldPassWhenTradingEnabledIsNull() {
        TradingEnabledRule rule = new TradingEnabledRule(true);
        RiskResult result = rule.evaluate(requestWith(null), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldPassWhenRuleIsDisabled() {
        TradingEnabledRule rule = new TradingEnabledRule(false);
        assertThat(rule.isEnabled()).isFalse();
        // Disabled rules are skipped by RiskEvaluationService — evaluate still works defensively
        RiskResult result = rule.evaluate(requestWith(false), context);
        assertThat(result.getResult()).isEqualTo("REJECTED"); // rule logic still rejects if called
    }

    @Test
    void shouldReturnCorrectRuleCode() {
        TradingEnabledRule rule = new TradingEnabledRule(true);
        assertThat(rule.getRuleCode()).isEqualTo("RULE_TRADING_ENABLED");
    }
}
