package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateOrderRuleTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final RiskContext context = RiskContext.builder()
            .orderId(UUID.randomUUID())
            .clientId(UUID.randomUUID())
            .build();

    private RiskEvaluationRequest limitBuyRequest(UUID clientId, String symbol, int qty, String limitPrice) {
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setClientId(clientId);
        req.setSymbol(symbol);
        req.setSide("BUY");
        req.setOrderType("LIMIT");
        req.setQuantity(qty);
        req.setLimitPrice(limitPrice);
        return req;
    }

    private RiskEvaluationRequest marketBuyRequest(UUID clientId, String symbol, int qty) {
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setClientId(clientId);
        req.setSymbol(symbol);
        req.setSide("BUY");
        req.setOrderType("MARKET");
        req.setQuantity(qty);
        req.setLimitPrice(null);
        return req;
    }

    @Test
    void shouldPassWhenFirstOrderForKey() {
        UUID clientId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        DuplicateOrderRule rule = new DuplicateOrderRule(redisTemplate, true);
        RiskResult result = rule.evaluate(limitBuyRequest(clientId, "AAPL", 100, "150.00"), context);

        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldRejectWhenDuplicateKeyAlreadyExists() {
        UUID clientId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        DuplicateOrderRule rule = new DuplicateOrderRule(redisTemplate, true);
        RiskResult result = rule.evaluate(limitBuyRequest(clientId, "AAPL", 100, "150.00"), context);

        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("RULE_DUPLICATE_ORDER");
        assertThat(result.getReason()).contains("DUPLICATE_ORDER");
    }

    @Test
    void shouldRejectWhenRedisReturnsNull() {
        UUID clientId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(null);

        DuplicateOrderRule rule = new DuplicateOrderRule(redisTemplate, true);
        RiskResult result = rule.evaluate(limitBuyRequest(clientId, "AAPL", 100, "150.00"), context);

        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getReason()).contains("DUPLICATE_ORDER");
    }

    @Test
    void shouldUseMarketSegmentWhenOrderTypeIsMarket() {
        UUID clientId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        DuplicateOrderRule rule = new DuplicateOrderRule(redisTemplate, true);
        rule.evaluate(marketBuyRequest(clientId, "TSLA", 50), context);

        verify(valueOperations).setIfAbsent(
                org.mockito.ArgumentMatchers.contains(":MARKET"),
                eq("1"),
                eq(Duration.ofSeconds(30))
        );
    }

    @Test
    void shouldUseMarketSegmentWhenLimitPriceIsNull() {
        UUID clientId = UUID.randomUUID();
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setClientId(clientId);
        req.setSymbol("MSFT");
        req.setSide("BUY");
        req.setOrderType("LIMIT");
        req.setQuantity(10);
        req.setLimitPrice(null);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        DuplicateOrderRule rule = new DuplicateOrderRule(redisTemplate, true);
        rule.evaluate(req, context);

        verify(valueOperations).setIfAbsent(
                org.mockito.ArgumentMatchers.contains(":MARKET"),
                eq("1"),
                eq(Duration.ofSeconds(30))
        );
    }

    @Test
    void shouldUseLimitPriceInKeyWhenProvided() {
        UUID clientId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        DuplicateOrderRule rule = new DuplicateOrderRule(redisTemplate, true);
        rule.evaluate(limitBuyRequest(clientId, "AAPL", 100, "175.50"), context);

        verify(valueOperations).setIfAbsent(
                org.mockito.ArgumentMatchers.contains(":175.50"),
                eq("1"),
                eq(Duration.ofSeconds(30))
        );
    }

    @Test
    void shouldSetTtlOf30Seconds() {
        UUID clientId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        DuplicateOrderRule rule = new DuplicateOrderRule(redisTemplate, true);
        rule.evaluate(limitBuyRequest(clientId, "AAPL", 100, "150.00"), context);

        verify(valueOperations).setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(30)));
    }

    @Test
    void shouldReturnCorrectRuleCode() {
        DuplicateOrderRule rule = new DuplicateOrderRule(redisTemplate, true);
        assertThat(rule.getRuleCode()).isEqualTo("RULE_DUPLICATE_ORDER");
    }

    @Test
    void shouldReportDisabledCorrectly() {
        DuplicateOrderRule rule = new DuplicateOrderRule(redisTemplate, false);
        assertThat(rule.isEnabled()).isFalse();
    }
}
