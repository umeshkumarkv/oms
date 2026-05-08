package com.brokerdealer.riskservice.service;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRuleConfig;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import com.brokerdealer.riskservice.repository.RiskRuleConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskEvaluationServiceTest {

    @Mock
    private RiskRuleConfigRepository riskRuleConfigRepository;

    @InjectMocks
    private RiskEvaluationService riskEvaluationService;

    private RiskEvaluationRequest sampleRequest() {
        RiskEvaluationRequest request = new RiskEvaluationRequest();
        request.setOrderId(UUID.randomUUID());
        request.setClientId(UUID.randomUUID());
        request.setSymbol("AAPL");
        request.setSide("BUY");
        request.setOrderType("LIMIT");
        request.setQuantity(100);
        request.setLimitPrice("150.00");
        request.setOrderValue("15000.00");
        return request;
    }

    private RiskRuleConfig qtyLimitConfig(boolean enabled, String threshold, int priority) {
        return RiskRuleConfig.builder()
                .ruleCode("SINGLE_ORDER_QTY_LIMIT")
                .enabled(enabled)
                .thresholdValue(new BigDecimal(threshold))
                .priority(priority)
                .updatedAt(Instant.now())
                .build();
    }

    private RiskRuleConfig valueLimitConfig(boolean enabled, String threshold, int priority) {
        return RiskRuleConfig.builder()
                .ruleCode("ORDER_VALUE_LIMIT")
                .enabled(enabled)
                .thresholdValue(new BigDecimal(threshold))
                .priority(priority)
                .updatedAt(Instant.now())
                .build();
    }

    @BeforeEach
    void setUp() {
        when(riskRuleConfigRepository.findAllByOrderByPriorityAsc()).thenReturn(
                List.of(qtyLimitConfig(true, "10000", 1), valueLimitConfig(true, "500000", 2)));
        riskEvaluationService.loadRules();
    }

    @Test
    void shouldReturnPassedWhenAllEnabledRulesPass() {
        RiskEvaluationRequest request = sampleRequest();

        RiskResult result = riskEvaluationService.evaluate(request);

        assertThat(result.getResult()).isEqualTo("PASSED");
        assertThat(result.getFailedRule()).isNull();
    }

    @Test
    void shouldReturnRejectedWhenQtyExceedsLimit() {
        RiskEvaluationRequest request = sampleRequest();
        request.setQuantity(15000);
        request.setOrderValue("2250000.00");

        RiskResult result = riskEvaluationService.evaluate(request);

        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("SINGLE_ORDER_QTY_LIMIT");
    }

    @Test
    void shouldReturnRejectedWhenOrderValueExceedsLimit() {
        RiskEvaluationRequest request = sampleRequest();
        request.setQuantity(100);
        request.setOrderValue("600000.00");

        RiskResult result = riskEvaluationService.evaluate(request);

        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("ORDER_VALUE_LIMIT");
    }

    @Test
    void shouldStopAtFirstFailedRuleAndNotEvaluateFurtherRules() {
        RiskEvaluationRequest request = sampleRequest();
        request.setQuantity(15000);
        request.setOrderValue("600000.00");

        RiskResult result = riskEvaluationService.evaluate(request);

        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("SINGLE_ORDER_QTY_LIMIT");
    }

    @Test
    void shouldSkipDisabledRulesDuringEvaluation() {
        // qty rule is disabled — order with qty=15000 should NOT be rejected
        when(riskRuleConfigRepository.findAllByOrderByPriorityAsc()).thenReturn(
                List.of(qtyLimitConfig(false, "10000", 1), valueLimitConfig(true, "500000", 2)));
        riskEvaluationService.loadRules();

        RiskEvaluationRequest request = sampleRequest();
        request.setQuantity(15000);
        request.setOrderValue("400000.00"); // value passes

        RiskResult result = riskEvaluationService.evaluate(request);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldReturnPassedWhenAllRulesDisabled() {
        when(riskRuleConfigRepository.findAllByOrderByPriorityAsc()).thenReturn(
                List.of(qtyLimitConfig(false, "10000", 1), valueLimitConfig(false, "500000", 2)));
        riskEvaluationService.loadRules();

        RiskResult result = riskEvaluationService.evaluate(sampleRequest());

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldHandleUnknownRuleCodeWithStub() {
        RiskRuleConfig unknownConfig = RiskRuleConfig.builder()
                .ruleCode("FUTURE_UNKNOWN_RULE")
                .enabled(true)
                .thresholdValue(null)
                .priority(1)
                .updatedAt(Instant.now())
                .build();
        when(riskRuleConfigRepository.findAllByOrderByPriorityAsc()).thenReturn(List.of(unknownConfig));
        riskEvaluationService.loadRules();

        RiskResult result = riskEvaluationService.evaluate(sampleRequest());

        assertThat(result.getResult()).isEqualTo("PASSED");
    }
}
