package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SufficientHoldingsRuleTest {

    private final RiskContext context = RiskContext.builder()
            .orderId(UUID.randomUUID())
            .clientId(UUID.randomUUID())
            .build();

    private RiskEvaluationRequest sellRequest(Integer holdingsForSymbol, int quantity) {
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setSide("SELL");
        req.setHoldingsForSymbol(holdingsForSymbol);
        req.setQuantity(quantity);
        return req;
    }

    private RiskEvaluationRequest buyRequest(Integer holdingsForSymbol, int quantity) {
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setSide("BUY");
        req.setHoldingsForSymbol(holdingsForSymbol);
        req.setQuantity(quantity);
        return req;
    }

    @Test
    void shouldPassSellWhenHoldingsEqualQuantity() {
        SufficientHoldingsRule rule = new SufficientHoldingsRule(true);
        RiskResult result = rule.evaluate(sellRequest(100, 100), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldPassSellWhenHoldingsExceedQuantity() {
        SufficientHoldingsRule rule = new SufficientHoldingsRule(true);
        RiskResult result = rule.evaluate(sellRequest(200, 100), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldRejectSellWhenHoldingsBelowQuantity() {
        SufficientHoldingsRule rule = new SufficientHoldingsRule(true);
        RiskResult result = rule.evaluate(sellRequest(50, 100), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("RULE_SUFFICIENT_HOLDINGS");
        assertThat(result.getReason()).contains("INSUFFICIENT_HOLDINGS");
        assertThat(result.getReason()).contains("50");
        assertThat(result.getReason()).contains("100");
    }

    @Test
    void shouldPassBuyAlways() {
        SufficientHoldingsRule rule = new SufficientHoldingsRule(true);
        // BUY side: holdings check doesn't apply
        RiskResult result = rule.evaluate(buyRequest(0, 1000), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldRejectSellWhenHoldingsIsNullAndQuantityPositive() {
        SufficientHoldingsRule rule = new SufficientHoldingsRule(true);
        // null holdingsForSymbol treated as 0
        RiskResult result = rule.evaluate(sellRequest(null, 10), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getReason()).contains("Holdings 0");
    }

    @Test
    void shouldReturnCorrectRuleCode() {
        SufficientHoldingsRule rule = new SufficientHoldingsRule(true);
        assertThat(rule.getRuleCode()).isEqualTo("RULE_SUFFICIENT_HOLDINGS");
    }

    @Test
    void shouldReportDisabledCorrectly() {
        SufficientHoldingsRule rule = new SufficientHoldingsRule(false);
        assertThat(rule.isEnabled()).isFalse();
    }
}
