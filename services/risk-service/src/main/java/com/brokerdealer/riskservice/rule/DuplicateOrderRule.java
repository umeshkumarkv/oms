package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@RequiredArgsConstructor
public class DuplicateOrderRule implements RiskRule {

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return "RULE_DUPLICATE_ORDER";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public RiskResult evaluate(RiskEvaluationRequest request, RiskContext context) {
        String limitPriceSegment = ("MARKET".equals(request.getOrderType()) || request.getLimitPrice() == null)
                ? "MARKET" : request.getLimitPrice();
        String key = "dup:" + request.getClientId()
                + ":" + request.getSymbol()
                + ":" + request.getSide()
                + ":" + request.getQuantity()
                + ":" + limitPriceSegment;

        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(isNew)) {
            return RiskResult.rejected(getRuleCode(),
                    "DUPLICATE_ORDER: Identical order already submitted within the last 30 seconds"
                            + " [clientId=" + request.getClientId()
                            + " symbol=" + request.getSymbol()
                            + " side=" + request.getSide()
                            + " qty=" + request.getQuantity() + "]");
        }
        return RiskResult.passed();
    }
}
