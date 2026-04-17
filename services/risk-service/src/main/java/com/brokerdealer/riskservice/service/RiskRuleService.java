package com.brokerdealer.riskservice.service;

import com.brokerdealer.riskservice.repository.RiskRuleConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskRuleService {

    private final RiskRuleConfigRepository riskRuleConfigRepository;

    public Object getRules() {
        // TODO: implement — JIRA story BD-XX
        return null;
    }

    public Optional<Object> updateRule(String ruleCode, Object request) {
        // TODO: implement — JIRA story BD-XX
        return Optional.empty();
    }
}
