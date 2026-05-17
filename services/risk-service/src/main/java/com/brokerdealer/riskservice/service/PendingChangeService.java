package com.brokerdealer.riskservice.service;

import com.brokerdealer.riskservice.domain.PendingChangeStatus;
import com.brokerdealer.riskservice.domain.PendingChangeType;
import com.brokerdealer.riskservice.domain.RiskRuleConfig;
import com.brokerdealer.riskservice.domain.RiskRulePendingChange;
import com.brokerdealer.riskservice.dto.request.UpdateRiskRuleRequest;
import com.brokerdealer.riskservice.dto.response.PendingChangeResponse;
import com.brokerdealer.riskservice.exception.ChangeAlreadyReviewedException;
import com.brokerdealer.riskservice.exception.PendingChangeNotFoundException;
import com.brokerdealer.riskservice.exception.RuleNotFoundException;
import com.brokerdealer.riskservice.exception.SelfApprovalNotAllowedException;
import com.brokerdealer.riskservice.exception.ValidationException;
import com.brokerdealer.riskservice.repository.RiskRuleConfigRepository;
import com.brokerdealer.riskservice.repository.RiskRulePendingChangeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PendingChangeService {

    private final RiskRulePendingChangeRepository pendingChangeRepository;
    private final RiskRuleConfigRepository riskRuleConfigRepository;
    private final RiskEvaluationService riskEvaluationService;

    @Transactional
    public PendingChangeResponse submitPendingChange(String ruleCode,
                                                     UpdateRiskRuleRequest request,
                                                     String makerUserId,
                                                     String makerUsername) {
        if (request.getEnabled() == null && request.getThresholdValue() == null) {
            throw new ValidationException("VALIDATION_ERROR",
                    "At least one of 'enabled' or 'thresholdValue' must be provided");
        }

        if (!riskRuleConfigRepository.existsById(ruleCode)) {
            throw new RuleNotFoundException("RULE_NOT_FOUND", "Risk rule not found: " + ruleCode);
        }

        PendingChangeType changeType = resolveChangeType(request);

        RiskRulePendingChange pendingChange = RiskRulePendingChange.builder()
                .ruleCode(ruleCode)
                .changeType(changeType)
                .proposedEnabled(request.getEnabled())
                .proposedThreshold(request.getThresholdValue())
                .makerUserId(makerUserId)
                .makerUsername(makerUsername)
                .status(PendingChangeStatus.PENDING)
                .makerNote(request.getChangeReason())
                .submittedAt(Instant.now())
                .build();

        RiskRulePendingChange saved = pendingChangeRepository.save(pendingChange);
        log.info("Pending change submitted: changeId={} ruleCode={} changeType={} makerUserId={}",
                saved.getId(), ruleCode, changeType, makerUserId);
        return toResponse(saved);
    }

    public List<PendingChangeResponse> listPendingChanges(String statusFilter) {
        if (statusFilter != null && !statusFilter.isBlank()) {
            PendingChangeStatus status;
            try {
                status = PendingChangeStatus.valueOf(statusFilter.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("VALIDATION_ERROR",
                        "Invalid status filter: " + statusFilter + ". Must be PENDING, APPROVED, or REJECTED");
            }
            return pendingChangeRepository.findByStatusOrderBySubmittedAtDesc(status)
                    .stream().map(this::toResponse).toList();
        }
        return pendingChangeRepository.findAllByOrderBySubmittedAtDesc()
                .stream().map(this::toResponse).toList();
    }

    public PendingChangeResponse getPendingChange(UUID changeId) {
        return toResponse(findById(changeId));
    }

    @Transactional
    public PendingChangeResponse approveChange(UUID changeId, String checkerUserId) {
        RiskRulePendingChange change = findById(changeId);

        if (change.getStatus() != PendingChangeStatus.PENDING) {
            throw new ChangeAlreadyReviewedException("CHANGE_ALREADY_REVIEWED",
                    "Pending change " + changeId + " has already been " + change.getStatus().name().toLowerCase());
        }

        if (change.getMakerUserId().equals(checkerUserId)) {
            throw new SelfApprovalNotAllowedException("SELF_APPROVAL_NOT_ALLOWED",
                    "The checker must not be the same user who submitted the change");
        }

        RiskRuleConfig rule = riskRuleConfigRepository.findById(change.getRuleCode())
                .orElseThrow(() -> new RuleNotFoundException("RULE_NOT_FOUND",
                        "Risk rule not found: " + change.getRuleCode()));

        applyChange(rule, change);
        riskRuleConfigRepository.save(rule);

        change.setStatus(PendingChangeStatus.APPROVED);
        change.setCheckerUserId(checkerUserId);
        change.setReviewedAt(Instant.now());
        RiskRulePendingChange saved = pendingChangeRepository.save(change);

        riskEvaluationService.loadRules();
        log.info("Pending change approved: changeId={} ruleCode={} checkerUserId={}",
                changeId, change.getRuleCode(), checkerUserId);
        return toResponse(saved);
    }

    @Transactional
    public PendingChangeResponse rejectChange(UUID changeId, String checkerUserId, String rejectionReason) {
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new ValidationException("VALIDATION_ERROR", "rejectionReason is required when rejecting a change");
        }

        RiskRulePendingChange change = findById(changeId);

        if (change.getStatus() != PendingChangeStatus.PENDING) {
            throw new ChangeAlreadyReviewedException("CHANGE_ALREADY_REVIEWED",
                    "Pending change " + changeId + " has already been " + change.getStatus().name().toLowerCase());
        }

        change.setStatus(PendingChangeStatus.REJECTED);
        change.setCheckerUserId(checkerUserId);
        change.setRejectionReason(rejectionReason);
        change.setReviewedAt(Instant.now());
        RiskRulePendingChange saved = pendingChangeRepository.save(change);

        log.info("Pending change rejected: changeId={} ruleCode={} checkerUserId={}",
                changeId, change.getRuleCode(), checkerUserId);
        return toResponse(saved);
    }

    private RiskRulePendingChange findById(UUID changeId) {
        return pendingChangeRepository.findById(changeId)
                .orElseThrow(() -> new PendingChangeNotFoundException("PENDING_CHANGE_NOT_FOUND",
                        "Pending change not found: " + changeId));
    }

    private PendingChangeType resolveChangeType(UpdateRiskRuleRequest request) {
        if (request.getEnabled() != null) {
            return Boolean.TRUE.equals(request.getEnabled())
                    ? PendingChangeType.ENABLE
                    : PendingChangeType.DISABLE;
        }
        return PendingChangeType.UPDATE_THRESHOLD;
    }

    private void applyChange(RiskRuleConfig rule, RiskRulePendingChange change) {
        if (change.getProposedEnabled() != null) {
            rule.setEnabled(change.getProposedEnabled());
        }
        if (change.getProposedThreshold() != null) {
            rule.setThresholdValue(change.getProposedThreshold());
        }
    }

    private PendingChangeResponse toResponse(RiskRulePendingChange change) {
        return PendingChangeResponse.builder()
                .id(change.getId())
                .ruleCode(change.getRuleCode())
                .changeType(change.getChangeType())
                .proposedEnabled(change.getProposedEnabled())
                .proposedThreshold(change.getProposedThreshold() != null
                        ? change.getProposedThreshold().toPlainString() : null)
                .makerUserId(change.getMakerUserId())
                .makerUsername(change.getMakerUsername())
                .status(change.getStatus())
                .makerNote(change.getMakerNote())
                .rejectionReason(change.getRejectionReason())
                .checkerUserId(change.getCheckerUserId())
                .submittedAt(change.getSubmittedAt())
                .reviewedAt(change.getReviewedAt())
                .build();
    }
}
