package com.brokerdealer.riskservice.service;

import com.brokerdealer.riskservice.domain.*;
import com.brokerdealer.riskservice.dto.request.UpdateRiskRuleRequest;
import com.brokerdealer.riskservice.dto.response.PendingChangeResponse;
import com.brokerdealer.riskservice.exception.*;
import com.brokerdealer.riskservice.repository.RiskRuleConfigRepository;
import com.brokerdealer.riskservice.repository.RiskRulePendingChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendingChangeServiceTest {

    @Mock
    private RiskRulePendingChangeRepository pendingChangeRepository;

    @Mock
    private RiskRuleConfigRepository riskRuleConfigRepository;

    @Mock
    private RiskEvaluationService riskEvaluationService;

    @InjectMocks
    private PendingChangeService pendingChangeService;

    private static final String RULE_CODE = "SINGLE_ORDER_QTY_LIMIT";
    private static final String MAKER_ID = "maker-uuid";
    private static final String MAKER_NAME = "admin01";
    private static final String CHECKER_ID = "checker-uuid";

    private RiskRuleConfig sampleRule;

    @BeforeEach
    void setUp() {
        sampleRule = RiskRuleConfig.builder()
                .ruleCode(RULE_CODE)
                .ruleName("Single Order Qty Limit")
                .ruleCategory("FINANCIAL_LIMIT")
                .enabled(true)
                .thresholdValue(new BigDecimal("10000.0000"))
                .thresholdUnit("SHARES")
                .action("REJECT")
                .priority(1)
                .updatedAt(Instant.now())
                .build();
    }

    private RiskRulePendingChange buildPendingChange(UUID id, PendingChangeType type, PendingChangeStatus status) {
        return RiskRulePendingChange.builder()
                .id(id)
                .ruleCode(RULE_CODE)
                .changeType(type)
                .proposedEnabled(type == PendingChangeType.ENABLE ? Boolean.TRUE
                        : type == PendingChangeType.DISABLE ? Boolean.FALSE : null)
                .proposedThreshold(type == PendingChangeType.UPDATE_THRESHOLD
                        ? new BigDecimal("5000.0000") : null)
                .currentEnabled(type == PendingChangeType.ENABLE ? Boolean.FALSE
                        : type == PendingChangeType.DISABLE ? Boolean.TRUE : null)
                .currentThreshold(type == PendingChangeType.UPDATE_THRESHOLD ? "10000" : null)
                .makerUserId(MAKER_ID)
                .makerUsername(MAKER_NAME)
                .status(status)
                .submittedAt(Instant.now())
                .build();
    }

    // ─── submitPendingChange ──────────────────────────────────────────────────

    @Test
    void shouldSubmitPendingChangeWithChangeTypeDisableWhenEnabledFalse() {
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setEnabled(false);
        request.setChangeReason("Disabling for test");

        UUID changeId = UUID.randomUUID();
        RiskRulePendingChange saved = buildPendingChange(changeId, PendingChangeType.DISABLE, PendingChangeStatus.PENDING);

        when(riskRuleConfigRepository.findById(RULE_CODE)).thenReturn(Optional.of(sampleRule));
        when(pendingChangeRepository.save(any())).thenReturn(saved);

        PendingChangeResponse response = pendingChangeService.submitPendingChange(RULE_CODE, request, MAKER_ID, MAKER_NAME);

        assertThat(response.getId()).isEqualTo(changeId);
        assertThat(response.getStatus()).isEqualTo(PendingChangeStatus.PENDING);
        assertThat(response.getChangeType()).isEqualTo(PendingChangeType.DISABLE);

        ArgumentCaptor<RiskRulePendingChange> captor = ArgumentCaptor.forClass(RiskRulePendingChange.class);
        verify(pendingChangeRepository).save(captor.capture());
        assertThat(captor.getValue().getChangeType()).isEqualTo(PendingChangeType.DISABLE);
        assertThat(captor.getValue().getMakerUserId()).isEqualTo(MAKER_ID);
        assertThat(captor.getValue().getMakerUsername()).isEqualTo(MAKER_NAME);
    }

    @Test
    void shouldSubmitPendingChangeWithChangeTypeEnableWhenEnabledTrue() {
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setEnabled(true);

        UUID changeId = UUID.randomUUID();
        RiskRulePendingChange saved = buildPendingChange(changeId, PendingChangeType.ENABLE, PendingChangeStatus.PENDING);

        when(riskRuleConfigRepository.findById(RULE_CODE)).thenReturn(Optional.of(sampleRule));
        when(pendingChangeRepository.save(any())).thenReturn(saved);

        PendingChangeResponse response = pendingChangeService.submitPendingChange(RULE_CODE, request, MAKER_ID, MAKER_NAME);

        assertThat(response.getChangeType()).isEqualTo(PendingChangeType.ENABLE);
    }

    @Test
    void shouldSubmitPendingChangeWithChangeTypeUpdateThresholdWhenThresholdProvided() {
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setThresholdValue(new BigDecimal("5000.0000"));

        UUID changeId = UUID.randomUUID();
        RiskRulePendingChange saved = buildPendingChange(changeId, PendingChangeType.UPDATE_THRESHOLD, PendingChangeStatus.PENDING);

        when(riskRuleConfigRepository.findById(RULE_CODE)).thenReturn(Optional.of(sampleRule));
        when(pendingChangeRepository.save(any())).thenReturn(saved);

        PendingChangeResponse response = pendingChangeService.submitPendingChange(RULE_CODE, request, MAKER_ID, MAKER_NAME);

        assertThat(response.getChangeType()).isEqualTo(PendingChangeType.UPDATE_THRESHOLD);
    }

    @Test
    void shouldThrowValidationExceptionWhenNeitherEnabledNorThresholdProvided() {
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();

        assertThatThrownBy(() -> pendingChangeService.submitPendingChange(RULE_CODE, request, MAKER_ID, MAKER_NAME))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");

        verifyNoInteractions(riskRuleConfigRepository);
    }

    @Test
    void shouldThrowRuleNotFoundExceptionWhenRuleCodeUnknown() {
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setEnabled(false);

        when(riskRuleConfigRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pendingChangeService.submitPendingChange("UNKNOWN", request, MAKER_ID, MAKER_NAME))
                .isInstanceOf(RuleNotFoundException.class)
                .extracting("errorCode").isEqualTo("RULE_NOT_FOUND");
    }

    @Test
    void shouldNotApplyChangeToRuleConfigWhenSubmitted() {
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setEnabled(false);

        UUID changeId = UUID.randomUUID();
        RiskRulePendingChange saved = buildPendingChange(changeId, PendingChangeType.DISABLE, PendingChangeStatus.PENDING);

        when(riskRuleConfigRepository.findById(RULE_CODE)).thenReturn(Optional.of(sampleRule));
        when(pendingChangeRepository.save(any())).thenReturn(saved);

        pendingChangeService.submitPendingChange(RULE_CODE, request, MAKER_ID, MAKER_NAME);

        verify(riskRuleConfigRepository, never()).save(any());
        verifyNoInteractions(riskEvaluationService);
    }

    @Test
    void shouldPopulateCurrentEnabledTrueWhenDisableChangeSubmitted() {
        sampleRule.setEnabled(true);
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setEnabled(false);

        UUID changeId = UUID.randomUUID();
        RiskRulePendingChange saved = buildPendingChange(changeId, PendingChangeType.DISABLE, PendingChangeStatus.PENDING);

        when(riskRuleConfigRepository.findById(RULE_CODE)).thenReturn(Optional.of(sampleRule));
        when(pendingChangeRepository.save(any())).thenReturn(saved);

        pendingChangeService.submitPendingChange(RULE_CODE, request, MAKER_ID, MAKER_NAME);

        ArgumentCaptor<RiskRulePendingChange> captor = ArgumentCaptor.forClass(RiskRulePendingChange.class);
        verify(pendingChangeRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentEnabled()).isTrue();
        assertThat(captor.getValue().getCurrentThreshold()).isEqualTo("10000.0000");
    }

    @Test
    void shouldPopulateCurrentEnabledFalseWhenEnableChangeSubmitted() {
        sampleRule.setEnabled(false);
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setEnabled(true);

        UUID changeId = UUID.randomUUID();
        RiskRulePendingChange saved = buildPendingChange(changeId, PendingChangeType.ENABLE, PendingChangeStatus.PENDING);

        when(riskRuleConfigRepository.findById(RULE_CODE)).thenReturn(Optional.of(sampleRule));
        when(pendingChangeRepository.save(any())).thenReturn(saved);

        pendingChangeService.submitPendingChange(RULE_CODE, request, MAKER_ID, MAKER_NAME);

        ArgumentCaptor<RiskRulePendingChange> captor = ArgumentCaptor.forClass(RiskRulePendingChange.class);
        verify(pendingChangeRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentEnabled()).isFalse();
    }

    @Test
    void shouldPopulateCurrentThresholdWhenUpdateThresholdChangeSubmitted() {
        sampleRule.setThresholdValue(new BigDecimal("500.0000"));
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setThresholdValue(new BigDecimal("1000.0000"));

        UUID changeId = UUID.randomUUID();
        RiskRulePendingChange saved = buildPendingChange(changeId, PendingChangeType.UPDATE_THRESHOLD, PendingChangeStatus.PENDING);

        when(riskRuleConfigRepository.findById(RULE_CODE)).thenReturn(Optional.of(sampleRule));
        when(pendingChangeRepository.save(any())).thenReturn(saved);

        pendingChangeService.submitPendingChange(RULE_CODE, request, MAKER_ID, MAKER_NAME);

        ArgumentCaptor<RiskRulePendingChange> captor = ArgumentCaptor.forClass(RiskRulePendingChange.class);
        verify(pendingChangeRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentThreshold()).isEqualTo("500.0000");
    }

    // ─── listPendingChanges ───────────────────────────────────────────────────

    @Test
    void shouldReturnAllChangesWhenNoStatusFilter() {
        UUID id = UUID.randomUUID();
        RiskRulePendingChange change = buildPendingChange(id, PendingChangeType.DISABLE, PendingChangeStatus.PENDING);

        when(pendingChangeRepository.findAllByOrderBySubmittedAtDesc()).thenReturn(List.of(change));

        List<PendingChangeResponse> result = pendingChangeService.listPendingChanges(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(id);
        verify(pendingChangeRepository).findAllByOrderBySubmittedAtDesc();
    }

    @Test
    void shouldFilterByStatusWhenStatusProvided() {
        UUID id = UUID.randomUUID();
        RiskRulePendingChange change = buildPendingChange(id, PendingChangeType.DISABLE, PendingChangeStatus.PENDING);

        when(pendingChangeRepository.findByStatusOrderBySubmittedAtDesc(PendingChangeStatus.PENDING))
                .thenReturn(List.of(change));

        List<PendingChangeResponse> result = pendingChangeService.listPendingChanges("PENDING");

        assertThat(result).hasSize(1);
        verify(pendingChangeRepository).findByStatusOrderBySubmittedAtDesc(PendingChangeStatus.PENDING);
    }

    @Test
    void shouldReturnEmptyListWhenNoChangesExist() {
        when(pendingChangeRepository.findAllByOrderBySubmittedAtDesc()).thenReturn(Collections.emptyList());

        List<PendingChangeResponse> result = pendingChangeService.listPendingChanges(null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowValidationExceptionForInvalidStatusFilter() {
        assertThatThrownBy(() -> pendingChangeService.listPendingChanges("INVALID"))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
    }

    // ─── getPendingChange ─────────────────────────────────────────────────────

    @Test
    void shouldReturnPendingChangeByIdWhenFound() {
        UUID id = UUID.randomUUID();
        RiskRulePendingChange change = buildPendingChange(id, PendingChangeType.DISABLE, PendingChangeStatus.PENDING);

        when(pendingChangeRepository.findById(id)).thenReturn(Optional.of(change));

        PendingChangeResponse response = pendingChangeService.getPendingChange(id);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getStatus()).isEqualTo(PendingChangeStatus.PENDING);
    }

    @Test
    void shouldThrowPendingChangeNotFoundExceptionWhenChangeIdUnknown() {
        UUID id = UUID.randomUUID();
        when(pendingChangeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pendingChangeService.getPendingChange(id))
                .isInstanceOf(PendingChangeNotFoundException.class)
                .extracting("errorCode").isEqualTo("PENDING_CHANGE_NOT_FOUND");
    }

    // ─── approveChange ────────────────────────────────────────────────────────

    @Test
    void shouldApproveChangeAndApplyToRuleWhenCheckerIsDifferentFromMaker() {
        UUID id = UUID.randomUUID();
        RiskRulePendingChange change = buildPendingChange(id, PendingChangeType.DISABLE, PendingChangeStatus.PENDING);

        RiskRulePendingChange approved = buildPendingChange(id, PendingChangeType.DISABLE, PendingChangeStatus.APPROVED);
        approved.setCheckerUserId(CHECKER_ID);
        approved.setReviewedAt(Instant.now());

        when(pendingChangeRepository.findById(id)).thenReturn(Optional.of(change));
        when(riskRuleConfigRepository.findById(RULE_CODE)).thenReturn(Optional.of(sampleRule));
        when(pendingChangeRepository.save(any())).thenReturn(approved);

        PendingChangeResponse response = pendingChangeService.approveChange(id, CHECKER_ID);

        assertThat(response.getStatus()).isEqualTo(PendingChangeStatus.APPROVED);
        assertThat(response.getCheckerUserId()).isEqualTo(CHECKER_ID);
        verify(riskRuleConfigRepository).save(argThat(r -> !r.getEnabled()));
        verify(riskEvaluationService).loadRules();
    }

    @Test
    void shouldApproveThresholdChangeAndUpdateRuleThreshold() {
        UUID id = UUID.randomUUID();
        RiskRulePendingChange change = buildPendingChange(id, PendingChangeType.UPDATE_THRESHOLD, PendingChangeStatus.PENDING);

        RiskRulePendingChange approved = buildPendingChange(id, PendingChangeType.UPDATE_THRESHOLD, PendingChangeStatus.APPROVED);
        approved.setCheckerUserId(CHECKER_ID);

        when(pendingChangeRepository.findById(id)).thenReturn(Optional.of(change));
        when(riskRuleConfigRepository.findById(RULE_CODE)).thenReturn(Optional.of(sampleRule));
        when(pendingChangeRepository.save(any())).thenReturn(approved);

        pendingChangeService.approveChange(id, CHECKER_ID);

        verify(riskRuleConfigRepository).save(argThat(r ->
                r.getThresholdValue().compareTo(new BigDecimal("5000.0000")) == 0));
    }

    @Test
    void shouldThrowSelfApprovalNotAllowedExceptionWhenCheckerIsMaker() {
        UUID id = UUID.randomUUID();
        RiskRulePendingChange change = buildPendingChange(id, PendingChangeType.DISABLE, PendingChangeStatus.PENDING);

        when(pendingChangeRepository.findById(id)).thenReturn(Optional.of(change));

        assertThatThrownBy(() -> pendingChangeService.approveChange(id, MAKER_ID))
                .isInstanceOf(SelfApprovalNotAllowedException.class)
                .extracting("errorCode").isEqualTo("SELF_APPROVAL_NOT_ALLOWED");

        verify(riskRuleConfigRepository, never()).save(any());
        verifyNoInteractions(riskEvaluationService);
    }

    @Test
    void shouldThrowChangeAlreadyReviewedExceptionWhenApproveOnAlreadyApprovedChange() {
        UUID id = UUID.randomUUID();
        RiskRulePendingChange change = buildPendingChange(id, PendingChangeType.DISABLE, PendingChangeStatus.APPROVED);

        when(pendingChangeRepository.findById(id)).thenReturn(Optional.of(change));

        assertThatThrownBy(() -> pendingChangeService.approveChange(id, CHECKER_ID))
                .isInstanceOf(ChangeAlreadyReviewedException.class)
                .extracting("errorCode").isEqualTo("CHANGE_ALREADY_REVIEWED");
    }

    @Test
    void shouldThrowChangeAlreadyReviewedExceptionWhenApproveOnRejectedChange() {
        UUID id = UUID.randomUUID();
        RiskRulePendingChange change = buildPendingChange(id, PendingChangeType.DISABLE, PendingChangeStatus.REJECTED);

        when(pendingChangeRepository.findById(id)).thenReturn(Optional.of(change));

        assertThatThrownBy(() -> pendingChangeService.approveChange(id, CHECKER_ID))
                .isInstanceOf(ChangeAlreadyReviewedException.class)
                .extracting("errorCode").isEqualTo("CHANGE_ALREADY_REVIEWED");
    }

    // ─── rejectChange ─────────────────────────────────────────────────────────

    @Test
    void shouldRejectChangeWithReasonAndSetStatusRejected() {
        UUID id = UUID.randomUUID();
        RiskRulePendingChange change = buildPendingChange(id, PendingChangeType.DISABLE, PendingChangeStatus.PENDING);

        RiskRulePendingChange rejected = buildPendingChange(id, PendingChangeType.DISABLE, PendingChangeStatus.REJECTED);
        rejected.setCheckerUserId(CHECKER_ID);
        rejected.setRejectionReason("Not approved for this quarter");
        rejected.setReviewedAt(Instant.now());

        when(pendingChangeRepository.findById(id)).thenReturn(Optional.of(change));
        when(pendingChangeRepository.save(any())).thenReturn(rejected);

        PendingChangeResponse response = pendingChangeService.rejectChange(id, CHECKER_ID, "Not approved for this quarter");

        assertThat(response.getStatus()).isEqualTo(PendingChangeStatus.REJECTED);
        assertThat(response.getRejectionReason()).isEqualTo("Not approved for this quarter");
        verify(riskRuleConfigRepository, never()).save(any());
        verifyNoInteractions(riskEvaluationService);
    }

    @Test
    void shouldThrowValidationExceptionWhenRejectionReasonIsNull() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> pendingChangeService.rejectChange(id, CHECKER_ID, null))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");

        verifyNoInteractions(pendingChangeRepository);
    }

    @Test
    void shouldThrowValidationExceptionWhenRejectionReasonIsBlank() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> pendingChangeService.rejectChange(id, CHECKER_ID, "   "))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void shouldThrowChangeAlreadyReviewedExceptionWhenRejectOnAlreadyReviewedChange() {
        UUID id = UUID.randomUUID();
        RiskRulePendingChange change = buildPendingChange(id, PendingChangeType.DISABLE, PendingChangeStatus.APPROVED);

        when(pendingChangeRepository.findById(id)).thenReturn(Optional.of(change));

        assertThatThrownBy(() -> pendingChangeService.rejectChange(id, CHECKER_ID, "Too late"))
                .isInstanceOf(ChangeAlreadyReviewedException.class)
                .extracting("errorCode").isEqualTo("CHANGE_ALREADY_REVIEWED");
    }
}
