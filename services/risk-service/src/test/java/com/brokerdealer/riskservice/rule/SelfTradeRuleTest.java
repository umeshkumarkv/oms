package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SelfTradeRuleTest {

    private final RiskContext context = RiskContext.builder()
            .orderId(UUID.randomUUID())
            .clientId(UUID.randomUUID())
            .build();

    private RiskEvaluationRequest requestWith(Boolean hasCrossingOpenOrder) {
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setClientId(UUID.randomUUID());
        req.setSymbol("AAPL");
        req.setSide("BUY");
        req.setHasCrossingOpenOrder(hasCrossingOpenOrder);
        return req;
    }

    @Test
    void shouldPassWhenNoCrossingOpenOrder() {
        SelfTradeRule rule = new SelfTradeRule(true);
        RiskResult result = rule.evaluate(requestWith(false), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldPassWhenHasCrossingOpenOrderIsNull() {
        SelfTradeRule rule = new SelfTradeRule(true);
        RiskResult result = rule.evaluate(requestWith(null), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldRejectWhenCrossingOpenOrderExists() {
        SelfTradeRule rule = new SelfTradeRule(true);
        RiskResult result = rule.evaluate(requestWith(true), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("RULE_SELF_TRADE");
        assertThat(result.getReason()).contains("SELF_TRADE_DETECTED");
    }

    @Test
    void shouldIncludeClientAndSymbolInRejectionReason() {
        SelfTradeRule rule = new SelfTradeRule(true);
        RiskEvaluationRequest req = requestWith(true);
        RiskResult result = rule.evaluate(req, context);
        assertThat(result.getReason()).contains("AAPL");
        assertThat(result.getReason()).contains("BUY");
    }

    @Test
    void shouldReturnCorrectRuleCode() {
        SelfTradeRule rule = new SelfTradeRule(true);
        assertThat(rule.getRuleCode()).isEqualTo("RULE_SELF_TRADE");
    }

    @Test
    void shouldReportDisabledCorrectly() {
        SelfTradeRule rule = new SelfTradeRule(false);
        assertThat(rule.isEnabled()).isFalse();
    }

    @Test
    void shouldStillRejectWhenEvaluateCalledDirectlyOnDisabledRule() {
        SelfTradeRule rule = new SelfTradeRule(false);
        RiskResult result = rule.evaluate(requestWith(true), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getReason()).contains("SELF_TRADE_DETECTED");
    }
}
