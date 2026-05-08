package com.brokerdealer.riskservice.service;

import com.brokerdealer.riskservice.domain.RiskRuleAudit;
import com.brokerdealer.riskservice.domain.RiskRuleConfig;
import com.brokerdealer.riskservice.dto.request.UpdateRiskRuleRequest;
import com.brokerdealer.riskservice.dto.response.RiskRuleAuditResponse;
import com.brokerdealer.riskservice.dto.response.RiskRuleResponse;
import com.brokerdealer.riskservice.exception.RuleNotFoundException;
import com.brokerdealer.riskservice.exception.ValidationException;
import com.brokerdealer.riskservice.mapper.RiskRuleMapper;
import com.brokerdealer.riskservice.repository.RiskRuleAuditRepository;
import com.brokerdealer.riskservice.repository.RiskRuleConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskRuleService {

    private final RiskRuleConfigRepository riskRuleConfigRepository;
    private final RiskRuleAuditRepository riskRuleAuditRepository;
    private final RiskRuleMapper riskRuleMapper;

    public List<RiskRuleResponse> getAllRules() {
        log.debug("Fetching all risk rules ordered by priority");
        return riskRuleConfigRepository.findAllByOrderByPriorityAsc()
                .stream()
                .map(riskRuleMapper::toResponse)
                .toList();
    }

    @Transactional
    public RiskRuleResponse updateRule(String ruleCode, UpdateRiskRuleRequest request, String changedBy) {
        if (request.getChangeReason() == null || request.getChangeReason().isBlank()) {
            throw new ValidationException("VALIDATION_ERROR", "changeReason is required");
        }

        RiskRuleConfig rule = riskRuleConfigRepository.findById(ruleCode)
                .orElseThrow(() -> new RuleNotFoundException("RULE_NOT_FOUND",
                        "Risk rule not found: " + ruleCode));

        log.info("Updating risk rule: ruleCode={} changedBy={}", ruleCode, changedBy);

        List<RiskRuleAudit> auditRecords = new ArrayList<>();

        if (request.getEnabled() != null && !request.getEnabled().equals(rule.getEnabled())) {
            auditRecords.add(buildAudit(ruleCode, "enabled",
                    String.valueOf(rule.getEnabled()),
                    String.valueOf(request.getEnabled()),
                    changedBy, request.getChangeReason()));
            rule.setEnabled(request.getEnabled());
        }

        if (request.getThresholdValue() != null) {
            String oldVal = rule.getThresholdValue() != null ? rule.getThresholdValue().toPlainString() : null;
            String newVal = request.getThresholdValue().toPlainString();
            if (rule.getThresholdValue() == null
                    || rule.getThresholdValue().compareTo(request.getThresholdValue()) != 0) {
                auditRecords.add(buildAudit(ruleCode, "thresholdValue",
                        oldVal, newVal, changedBy, request.getChangeReason()));
                rule.setThresholdValue(request.getThresholdValue());
            }
        }

        riskRuleConfigRepository.save(rule);
        if (!auditRecords.isEmpty()) {
            riskRuleAuditRepository.saveAll(auditRecords);
        }

        log.info("Risk rule updated: ruleCode={} auditRecordsCreated={}", ruleCode, auditRecords.size());
        return riskRuleMapper.toResponse(rule);
    }

    public List<RiskRuleAuditResponse> getAuditLog() {
        log.debug("Fetching risk rule audit log");
        return riskRuleAuditRepository.findAllByOrderByChangedAtDesc()
                .stream()
                .map(riskRuleMapper::toAuditResponse)
                .toList();
    }

    private RiskRuleAudit buildAudit(String ruleCode, String fieldChanged,
                                      String oldValue, String newValue,
                                      String changedBy, String changeReason) {
        return RiskRuleAudit.builder()
                .ruleCode(ruleCode)
                .fieldChanged(fieldChanged)
                .oldValue(oldValue)
                .newValue(newValue)
                .changedBy(changedBy)
                .changeReason(changeReason)
                .changedAt(Instant.now())
                .build();
    }
}
