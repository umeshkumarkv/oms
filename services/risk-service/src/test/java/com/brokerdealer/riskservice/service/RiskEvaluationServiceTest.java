package com.brokerdealer.riskservice.service;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRuleConfig;
import com.brokerdealer.riskservice.domain.RiskWarning;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import com.brokerdealer.riskservice.repository.RiskRuleConfigRepository;
import com.brokerdealer.riskservice.repository.RiskWarningRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskEvaluationServiceTest {

    @Mock
    private RiskRuleConfigRepository riskRuleConfigRepository;

    @Mock
    private RiskWarningRepository riskWarningRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

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

    @Test
    void shouldRejectDuplicateOrderWhenRedisKeyAlreadyExists() {
        RiskRuleConfig dupConfig = RiskRuleConfig.builder()
                .ruleCode("RULE_DUPLICATE_ORDER")
                .enabled(true)
                .thresholdValue(null)
                .priority(1)
                .updatedAt(Instant.now())
                .build();
        when(riskRuleConfigRepository.findAllByOrderByPriorityAsc()).thenReturn(List.of(dupConfig));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);
        riskEvaluationService.loadRules();

        RiskResult result = riskEvaluationService.evaluate(sampleRequest());

        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("RULE_DUPLICATE_ORDER");
    }

    @Test
    void shouldPassDuplicateOrderRuleWhenRedisKeyIsNew() {
        RiskRuleConfig dupConfig = RiskRuleConfig.builder()
                .ruleCode("RULE_DUPLICATE_ORDER")
                .enabled(true)
                .thresholdValue(null)
                .priority(1)
                .updatedAt(Instant.now())
                .build();
        when(riskRuleConfigRepository.findAllByOrderByPriorityAsc()).thenReturn(List.of(dupConfig));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        riskEvaluationService.loadRules();

        RiskResult result = riskEvaluationService.evaluate(sampleRequest());

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldRejectSelfTradeWhenCrossingOpenOrderExists() {
        RiskRuleConfig selfTradeConfig = RiskRuleConfig.builder()
                .ruleCode("RULE_SELF_TRADE")
                .enabled(true)
                .thresholdValue(null)
                .priority(1)
                .updatedAt(Instant.now())
                .build();
        when(riskRuleConfigRepository.findAllByOrderByPriorityAsc()).thenReturn(List.of(selfTradeConfig));
        riskEvaluationService.loadRules();

        RiskEvaluationRequest request = sampleRequest();
        request.setHasCrossingOpenOrder(true);

        RiskResult result = riskEvaluationService.evaluate(request);

        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("RULE_SELF_TRADE");
    }

    @Test
    void shouldPassSelfTradeRuleWhenNoCrossingOpenOrder() {
        RiskRuleConfig selfTradeConfig = RiskRuleConfig.builder()
                .ruleCode("RULE_SELF_TRADE")
                .enabled(true)
                .thresholdValue(null)
                .priority(1)
                .updatedAt(Instant.now())
                .build();
        when(riskRuleConfigRepository.findAllByOrderByPriorityAsc()).thenReturn(List.of(selfTradeConfig));
        riskEvaluationService.loadRules();

        RiskEvaluationRequest request = sampleRequest();
        request.setHasCrossingOpenOrder(false);

        RiskResult result = riskEvaluationService.evaluate(request);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    // ─── WARN rule wiring (SCRUM-122) ────────────────────────────────────────

    @Test
    void shouldPersistWarningAndContinueWhenWarnRuleTriggers() {
        // ORDER_VALUE_SPIKE_CHECK: orderValue=600000 vs avg=50000, multiplier=10 → spike at 500000 → WARNED
        RiskRuleConfig warnConfig = RiskRuleConfig.builder()
                .ruleCode("ORDER_VALUE_SPIKE_CHECK")
                .enabled(true)
                .thresholdValue(new BigDecimal("10"))
                .priority(1)
                .updatedAt(Instant.now())
                .build();
        when(riskRuleConfigRepository.findAllByOrderByPriorityAsc()).thenReturn(List.of(warnConfig));
        riskEvaluationService.loadRules();

        RiskEvaluationRequest request = sampleRequest();
        request.setOrderValue("600000.00");
        request.setAvgOrderValue("50000.00");

        RiskResult result = riskEvaluationService.evaluate(request);

        assertThat(result.getResult()).isEqualTo("PASSED");
        verify(riskWarningRepository).saveAll(argThat((List<RiskWarning> list) ->
                list.size() == 1 && "ORDER_VALUE_SPIKE_CHECK".equals(list.get(0).getRuleCode())));
    }

    @Test
    void shouldNotPersistWarningWhenWarnRuleIsDisabled() {
        RiskRuleConfig warnConfig = RiskRuleConfig.builder()
                .ruleCode("ORDER_VALUE_SPIKE_CHECK")
                .enabled(false)
                .thresholdValue(new BigDecimal("10"))
                .priority(1)
                .updatedAt(Instant.now())
                .build();
        when(riskRuleConfigRepository.findAllByOrderByPriorityAsc()).thenReturn(List.of(warnConfig));
        riskEvaluationService.loadRules();

        RiskEvaluationRequest request = sampleRequest();
        request.setOrderValue("600000.00");
        request.setAvgOrderValue("50000.00");

        riskEvaluationService.evaluate(request);

        verify(riskWarningRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldStopBeforeWarnRulesWhenRejectRuleFires() {
        // REJECT rule (priority 1) fires before WARN rule (priority 2) → WARN rule never evaluates
        RiskRuleConfig rejectConfig = RiskRuleConfig.builder()
                .ruleCode("SINGLE_ORDER_QTY_LIMIT")
                .enabled(true)
                .thresholdValue(new BigDecimal("100"))
                .priority(1)
                .updatedAt(Instant.now())
                .build();
        RiskRuleConfig warnConfig = RiskRuleConfig.builder()
                .ruleCode("ORDER_VALUE_SPIKE_CHECK")
                .enabled(true)
                .thresholdValue(new BigDecimal("10"))
                .priority(2)
                .updatedAt(Instant.now())
                .build();
        when(riskRuleConfigRepository.findAllByOrderByPriorityAsc()).thenReturn(List.of(rejectConfig, warnConfig));
        riskEvaluationService.loadRules();

        RiskEvaluationRequest request = sampleRequest();
        request.setQuantity(500);          // exceeds SINGLE_ORDER_QTY_LIMIT threshold of 100 → REJECTED
        request.setOrderValue("600000.00");
        request.setAvgOrderValue("50000.00");

        RiskResult result = riskEvaluationService.evaluate(request);

        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("SINGLE_ORDER_QTY_LIMIT");
        verify(riskWarningRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldPersistMultipleWarningsForMultipleWarnRules() {
        RiskRuleConfig warnConfig1 = RiskRuleConfig.builder()
                .ruleCode("ORDER_VALUE_SPIKE_CHECK")
                .enabled(true)
                .thresholdValue(new BigDecimal("10"))
                .priority(1)
                .updatedAt(Instant.now())
                .build();
        RiskRuleConfig warnConfig2 = RiskRuleConfig.builder()
                .ruleCode("NOTIONAL_VALUE_CHECK")
                .enabled(true)
                .thresholdValue(new BigDecimal("100000"))
                .priority(2)
                .updatedAt(Instant.now())
                .build();
        when(riskRuleConfigRepository.findAllByOrderByPriorityAsc()).thenReturn(List.of(warnConfig1, warnConfig2));
        riskEvaluationService.loadRules();

        RiskEvaluationRequest request = sampleRequest();
        request.setOrderValue("600000.00"); // triggers ORDER_VALUE_SPIKE_CHECK and NOTIONAL_VALUE_CHECK
        request.setAvgOrderValue("50000.00");

        RiskResult result = riskEvaluationService.evaluate(request);

        assertThat(result.getResult()).isEqualTo("PASSED");
        verify(riskWarningRepository).saveAll(argThat((List<RiskWarning> list) -> list.size() == 2));
    }
}
