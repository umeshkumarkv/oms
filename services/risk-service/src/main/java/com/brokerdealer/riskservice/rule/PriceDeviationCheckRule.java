package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RequiredArgsConstructor
@Slf4j
public class PriceDeviationCheckRule implements RiskRule {

    private static final BigDecimal THRESHOLD = new BigDecimal("0.10");

    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return "PRICE_DEVIATION_CHECK";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public RiskResult evaluate(RiskEvaluationRequest request, RiskContext context) {
        if ("MARKET".equals(request.getOrderType())) {
            log.debug("PRICE_DEVIATION_CHECK skipped: MARKET order — no limitPrice; orderId={}", context.getOrderId());
            return RiskResult.passed();
        }
        if (request.getLimitPrice() == null || request.getLastPrice() == null) {
            log.debug("PRICE_DEVIATION_CHECK skipped: limitPrice or lastPrice is null; orderId={}", context.getOrderId());
            return RiskResult.passed();
        }

        BigDecimal limitPrice = new BigDecimal(request.getLimitPrice());
        BigDecimal lastPrice  = new BigDecimal(request.getLastPrice());

        if (lastPrice.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("PRICE_DEVIATION_CHECK skipped: lastPrice is zero; orderId={}", context.getOrderId());
            return RiskResult.passed();
        }

        BigDecimal deviation = limitPrice.subtract(lastPrice).abs()
                .divide(lastPrice, 10, RoundingMode.HALF_UP);

        if (deviation.compareTo(THRESHOLD) > 0) {
            log.warn("PRICE_DEVIATION_CHECK triggered: orderId={} deviation={}", context.getOrderId(), deviation);
            return RiskResult.warned(getRuleCode(),
                    "PRICE_DEVIATION: limitPrice deviates " + deviation.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    + "% from lastPrice (threshold 10%)");
        }
        return RiskResult.passed();
    }
}
