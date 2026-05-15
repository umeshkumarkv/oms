package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PriceDeviationCheckRuleTest {

    private final RiskContext context = RiskContext.builder()
            .orderId(UUID.randomUUID())
            .clientId(UUID.randomUUID())
            .build();

    private RiskEvaluationRequest limitRequest(String limitPrice, String lastPrice) {
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setOrderType("LIMIT");
        req.setLimitPrice(limitPrice);
        req.setLastPrice(lastPrice);
        return req;
    }

    @Test
    void shouldWarnWhenLimitPriceDeviatesMoreThan10Percent() {
        // 166 vs 150: deviation = 16/150 = 10.67% > 10%
        PriceDeviationCheckRule rule = new PriceDeviationCheckRule(true);
        RiskResult result = rule.evaluate(limitRequest("166.00", "150.00"), context);

        assertThat(result.getResult()).isEqualTo("WARNED");
        assertThat(result.getFailedRule()).isEqualTo("PRICE_DEVIATION_CHECK");
        assertThat(result.getReason()).contains("PRICE_DEVIATION");
    }

    @Test
    void shouldPassWhenLimitPriceDeviatesLessThan10Percent() {
        // 155 vs 150: deviation = 5/150 = 3.33% < 10%
        PriceDeviationCheckRule rule = new PriceDeviationCheckRule(true);
        RiskResult result = rule.evaluate(limitRequest("155.00", "150.00"), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldPassWhenLimitPriceDeviatesExactlyAtThreshold() {
        // 165 vs 150: deviation = 15/150 = 0.10 exactly — compareTo(0.10) = 0, not > 0 → PASSED
        PriceDeviationCheckRule rule = new PriceDeviationCheckRule(true);
        RiskResult result = rule.evaluate(limitRequest("165.00", "150.00"), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldSkipWhenOrderTypeIsMarket() {
        PriceDeviationCheckRule rule = new PriceDeviationCheckRule(true);
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setOrderType("MARKET");
        req.setLastPrice("150.00");

        RiskResult result = rule.evaluate(req, context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldSkipWhenLimitPriceIsNull() {
        PriceDeviationCheckRule rule = new PriceDeviationCheckRule(true);
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setOrderType("LIMIT");
        req.setLimitPrice(null);
        req.setLastPrice("150.00");

        RiskResult result = rule.evaluate(req, context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldSkipWhenLastPriceIsNull() {
        PriceDeviationCheckRule rule = new PriceDeviationCheckRule(true);
        RiskResult result = rule.evaluate(limitRequest("155.00", null), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldReturnCorrectRuleCode() {
        PriceDeviationCheckRule rule = new PriceDeviationCheckRule(true);
        assertThat(rule.getRuleCode()).isEqualTo("PRICE_DEVIATION_CHECK");
    }

    @Test
    void shouldReportDisabledCorrectly() {
        PriceDeviationCheckRule rule = new PriceDeviationCheckRule(false);
        assertThat(rule.isEnabled()).isFalse();
    }
}
