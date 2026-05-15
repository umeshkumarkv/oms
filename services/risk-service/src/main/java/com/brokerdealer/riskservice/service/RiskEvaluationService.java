package com.brokerdealer.riskservice.service;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.domain.RiskRuleConfig;
import com.brokerdealer.riskservice.domain.RiskWarning;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import com.brokerdealer.riskservice.repository.RiskRuleConfigRepository;
import com.brokerdealer.riskservice.repository.RiskWarningRepository;
import com.brokerdealer.riskservice.rule.DailyOrderCountRule;
import com.brokerdealer.riskservice.rule.DuplicateOrderRule;
import com.brokerdealer.riskservice.rule.KycVerifiedRule;
import com.brokerdealer.riskservice.rule.MarginCallActiveRule;
import com.brokerdealer.riskservice.rule.MaxOrderQtyRule;
import com.brokerdealer.riskservice.rule.MaxOrderValueRule;
import com.brokerdealer.riskservice.rule.NotionalValueCheckRule;
import com.brokerdealer.riskservice.rule.OrderQtySpikeCheckRule;
import com.brokerdealer.riskservice.rule.OrderValueLimitRule;
import com.brokerdealer.riskservice.rule.OrderValueSpikeCheckRule;
import com.brokerdealer.riskservice.rule.PriceDeviationCheckRule;
import com.brokerdealer.riskservice.rule.SelfTradeRule;
import com.brokerdealer.riskservice.rule.SingleOrderQtyLimitRule;
import com.brokerdealer.riskservice.rule.StubRiskRule;
import com.brokerdealer.riskservice.rule.SufficientCashRule;
import com.brokerdealer.riskservice.rule.SufficientHoldingsRule;
import com.brokerdealer.riskservice.rule.TradingEnabledRule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskEvaluationService {

    private final RiskRuleConfigRepository riskRuleConfigRepository;
    private final RiskWarningRepository riskWarningRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private List<RiskRule> rules;

    @PostConstruct
    public void loadRules() {
        List<RiskRuleConfig> configs = riskRuleConfigRepository.findAllByOrderByPriorityAsc();
        rules = configs.stream()
                .map(this::buildRule)
                .collect(Collectors.toList());
        long enabledCount = rules.stream().filter(RiskRule::isEnabled).count();
        log.info("Risk rules loaded: total={} enabled={} codes={}", rules.size(), enabledCount,
                rules.stream().filter(RiskRule::isEnabled).map(RiskRule::getRuleCode).collect(Collectors.joining(",")));
    }

    public RiskResult evaluate(RiskEvaluationRequest request) {
        log.debug("Evaluating order: orderId={} qty={}", request.getOrderId(), request.getQuantity());
        RiskContext context = RiskContext.builder()
                .orderId(request.getOrderId())
                .clientId(request.getClientId())
                .build();
        List<RiskWarning> warnings = new ArrayList<>();

        for (RiskRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }
            RiskResult result = rule.evaluate(request, context);
            if ("REJECTED".equals(result.getResult())) {
                log.info("Order risk rejected: orderId={} rule={}", request.getOrderId(), result.getFailedRule());
                return result;
            }
            if ("WARNED".equals(result.getResult())) {
                log.warn("Risk warning: orderId={} rule={}", request.getOrderId(), result.getFailedRule());
                warnings.add(RiskWarning.builder()
                        .orderId(request.getOrderId())
                        .ruleCode(result.getFailedRule())
                        .reason(result.getReason())
                        .createdAt(Instant.now())
                        .build());
            }
        }

        if (!warnings.isEmpty()) {
            riskWarningRepository.saveAll(warnings);
            log.info("Risk warnings persisted: orderId={} count={}", request.getOrderId(), warnings.size());
        }

        log.info("Order risk passed: orderId={}", request.getOrderId());
        return RiskResult.passed();
    }

    private RiskRule buildRule(RiskRuleConfig config) {
        return switch (config.getRuleCode()) {
            case "SINGLE_ORDER_QTY_LIMIT"  -> new SingleOrderQtyLimitRule(config.getThresholdValue(), config.getEnabled());
            case "ORDER_VALUE_LIMIT"       -> new OrderValueLimitRule(config.getThresholdValue(), config.getEnabled());
            case "RULE_MAX_ORDER_QTY"      -> new MaxOrderQtyRule(config.getThresholdValue(), config.getEnabled());
            case "RULE_MAX_ORDER_VALUE"    -> new MaxOrderValueRule(config.getThresholdValue(), config.getEnabled());
            case "RULE_DUPLICATE_ORDER"    -> new DuplicateOrderRule(stringRedisTemplate, config.getEnabled());
            case "RULE_SELF_TRADE"         -> new SelfTradeRule(config.getEnabled());
            case "RULE_MARGIN_CALL_ACTIVE" -> new MarginCallActiveRule(config.getEnabled());
            case "RULE_TRADING_ENABLED"    -> new TradingEnabledRule(config.getEnabled());
            case "RULE_KYC_VERIFIED"       -> new KycVerifiedRule(config.getEnabled());
            case "RULE_SUFFICIENT_CASH"    -> new SufficientCashRule(config.getEnabled());
            case "RULE_SUFFICIENT_HOLDINGS"  -> new SufficientHoldingsRule(config.getEnabled());
            case "PRICE_DEVIATION_CHECK"     -> new PriceDeviationCheckRule(config.getThresholdValue(), config.getEnabled());
            case "NOTIONAL_VALUE_CHECK"      -> new NotionalValueCheckRule(config.getThresholdValue(), config.getEnabled());
            case "ORDER_QTY_SPIKE_CHECK"     -> new OrderQtySpikeCheckRule(config.getThresholdValue(), config.getEnabled());
            case "ORDER_VALUE_SPIKE_CHECK"   -> new OrderValueSpikeCheckRule(config.getThresholdValue(), config.getEnabled());
            case "RULE_DAILY_ORDER_COUNT"    -> new DailyOrderCountRule(config.getEnabled());
            default -> new StubRiskRule(config.getRuleCode(), config.getEnabled());
        };
    }
}
