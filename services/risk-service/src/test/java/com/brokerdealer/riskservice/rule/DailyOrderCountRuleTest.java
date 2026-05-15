package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DailyOrderCountRuleTest {

    private final RiskContext context = RiskContext.builder()
            .orderId(UUID.randomUUID())
            .clientId(UUID.randomUUID())
            .build();

    private RiskEvaluationRequest request(Integer dailyOrderCount, Integer maxDailyOrders) {
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setDailyOrderCount(dailyOrderCount);
        req.setMaxDailyOrders(maxDailyOrders);
        return req;
    }

    @Test
    void shouldWarnWhenDailyOrderCountReachesMax() {
        DailyOrderCountRule rule = new DailyOrderCountRule(true);
        RiskResult result = rule.evaluate(request(50, 50), context);

        assertThat(result.getResult()).isEqualTo("WARNED");
        assertThat(result.getFailedRule()).isEqualTo("RULE_DAILY_ORDER_COUNT");
        assertThat(result.getReason()).contains("DAILY_ORDER_COUNT");
    }

    @Test
    void shouldWarnWhenDailyOrderCountExceedsMax() {
        DailyOrderCountRule rule = new DailyOrderCountRule(true);
        RiskResult result = rule.evaluate(request(51, 50), context);

        assertThat(result.getResult()).isEqualTo("WARNED");
    }

    @Test
    void shouldPassWhenDailyOrderCountBelowMax() {
        DailyOrderCountRule rule = new DailyOrderCountRule(true);
        RiskResult result = rule.evaluate(request(49, 50), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldSkipWhenDailyOrderCountIsNull() {
        DailyOrderCountRule rule = new DailyOrderCountRule(true);
        RiskResult result = rule.evaluate(request(null, 50), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldSkipWhenMaxDailyOrdersIsNull() {
        DailyOrderCountRule rule = new DailyOrderCountRule(true);
        RiskResult result = rule.evaluate(request(50, null), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldReturnCorrectRuleCode() {
        DailyOrderCountRule rule = new DailyOrderCountRule(true);
        assertThat(rule.getRuleCode()).isEqualTo("RULE_DAILY_ORDER_COUNT");
    }

    @Test
    void shouldReportDisabledCorrectly() {
        DailyOrderCountRule rule = new DailyOrderCountRule(false);
        assertThat(rule.isEnabled()).isFalse();
    }
}
