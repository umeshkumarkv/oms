package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SufficientCashRuleTest {

    private final RiskContext context = RiskContext.builder()
            .orderId(UUID.randomUUID())
            .clientId(UUID.randomUUID())
            .build();

    private RiskEvaluationRequest buyRequest(String buyingPower, String orderValue) {
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setSide("BUY");
        req.setBuyingPower(buyingPower);
        req.setOrderValue(orderValue);
        req.setQuantity(10);
        return req;
    }

    private RiskEvaluationRequest sellRequest(String buyingPower, String orderValue) {
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setSide("SELL");
        req.setBuyingPower(buyingPower);
        req.setOrderValue(orderValue);
        req.setQuantity(10);
        return req;
    }

    @Test
    void shouldPassBuyWhenBuyingPowerEqualToOrderValue() {
        SufficientCashRule rule = new SufficientCashRule(true);
        RiskResult result = rule.evaluate(buyRequest("1500.00", "1500.00"), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldPassBuyWhenBuyingPowerExceedsOrderValue() {
        SufficientCashRule rule = new SufficientCashRule(true);
        RiskResult result = rule.evaluate(buyRequest("5000.00", "1500.00"), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldRejectBuyWhenBuyingPowerBelowOrderValue() {
        SufficientCashRule rule = new SufficientCashRule(true);
        RiskResult result = rule.evaluate(buyRequest("500.00", "1500.00"), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("RULE_SUFFICIENT_CASH");
        assertThat(result.getReason()).contains("INSUFFICIENT_BUYING_POWER");
        assertThat(result.getReason()).contains("500");
        assertThat(result.getReason()).contains("1500");
    }

    @Test
    void shouldPassSellAlways() {
        SufficientCashRule rule = new SufficientCashRule(true);
        // SELL side: buying power check doesn't apply
        RiskResult result = rule.evaluate(sellRequest("0.00", "99999.00"), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldPassWhenBuyingPowerIsNull() {
        SufficientCashRule rule = new SufficientCashRule(true);
        RiskResult result = rule.evaluate(buyRequest(null, "1500.00"), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldUseCompareToBigDecimalScaleIndependent() {
        SufficientCashRule rule = new SufficientCashRule(true);
        // 1500 == 1500.00 — scale difference must not cause false reject
        RiskResult result = rule.evaluate(buyRequest("1500", "1500.00"), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldReturnCorrectRuleCode() {
        SufficientCashRule rule = new SufficientCashRule(true);
        assertThat(rule.getRuleCode()).isEqualTo("RULE_SUFFICIENT_CASH");
    }

    @Test
    void shouldReportDisabledCorrectly() {
        SufficientCashRule rule = new SufficientCashRule(false);
        assertThat(rule.isEnabled()).isFalse();
    }
}
