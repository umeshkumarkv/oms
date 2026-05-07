package com.brokerdealer.riskservice.service;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.domain.RiskRuleConfig;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import com.brokerdealer.riskservice.repository.RiskRuleConfigRepository;
import com.brokerdealer.riskservice.rule.OrderValueLimitRule;
import com.brokerdealer.riskservice.rule.SingleOrderQtyLimitRule;
import com.brokerdealer.riskservice.rule.StubRiskRule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskEvaluationService {

    private final RiskRuleConfigRepository riskRuleConfigRepository;
    private List<RiskRule> enabledRules;

    @PostConstruct
    void loadRules() {
        List<RiskRuleConfig> configs = riskRuleConfigRepository.findByEnabledTrueOrderByPriority();
        enabledRules = configs.stream()
                .map(this::buildRule)
                .collect(Collectors.toList());
        log.info("Risk rules loaded: count={} codes={}", enabledRules.size(),
                enabledRules.stream().map(RiskRule::getRuleCode).collect(Collectors.joining(",")));
    }

    public RiskResult evaluate(RiskEvaluationRequest request) {
        log.debug("Evaluating order: orderId={} qty={}", request.getOrderId(), request.getQuantity());
        for (RiskRule rule : enabledRules) {
            RiskResult result = rule.evaluate(request);
            if ("REJECTED".equals(result.getResult())) {
                log.info("Order risk rejected: orderId={} rule={}", request.getOrderId(), result.getFailedRule());
                return result;
            }
        }
        log.info("Order risk passed: orderId={}", request.getOrderId());
        return RiskResult.passed();
    }

    private RiskRule buildRule(RiskRuleConfig config) {
        return switch (config.getRuleCode()) {
            case "SINGLE_ORDER_QTY_LIMIT" -> new SingleOrderQtyLimitRule(config.getThresholdValue());
            case "ORDER_VALUE_LIMIT" -> new OrderValueLimitRule(config.getThresholdValue());
            default -> new StubRiskRule(config.getRuleCode());
        };
    }
}
