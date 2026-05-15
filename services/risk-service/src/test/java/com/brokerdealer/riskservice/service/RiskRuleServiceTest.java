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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskRuleServiceTest {

    @Mock
    private RiskRuleConfigRepository riskRuleConfigRepository;

    @Mock
    private RiskRuleAuditRepository riskRuleAuditRepository;

    @Mock
    private RiskRuleMapper riskRuleMapper;

    @Mock
    private RiskEvaluationService riskEvaluationService;

    @InjectMocks
    private RiskRuleService riskRuleService;

    private RiskRuleConfig sampleRule;

    @BeforeEach
    void setUp() {
        sampleRule = RiskRuleConfig.builder()
                .ruleCode("PRICE_DEVIATION_CHECK")
                .ruleName("Price Deviation Check")
                .ruleCategory("FAT_FINGER")
                .enabled(false)
                .thresholdValue(new BigDecimal("10.0000"))
                .thresholdUnit("PERCENT")
                .description("Maximum % price deviation from last price")
                .action("REJECT")
                .priority(3)
                .updatedAt(Instant.now())
                .build();
    }

    // ─── getAllRules ──────────────────────────────────────────────────────────

    @Test
    void shouldReturnAllRulesOrderedByPriority() {
        RiskRuleResponse response = RiskRuleResponse.builder()
                .ruleCode("PRICE_DEVIATION_CHECK").ruleName("Price Deviation Check")
                .ruleCategory("FAT_FINGER").enabled(false).priority(3).action("REJECT").build();

        when(riskRuleConfigRepository.findAllByOrderByPriorityAsc()).thenReturn(List.of(sampleRule));
        when(riskRuleMapper.toResponse(sampleRule)).thenReturn(response);

        List<RiskRuleResponse> result = riskRuleService.getAllRules();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRuleCode()).isEqualTo("PRICE_DEVIATION_CHECK");
        verify(riskRuleConfigRepository).findAllByOrderByPriorityAsc();
    }

    @Test
    void shouldReturnEmptyListWhenNoRulesExist() {
        when(riskRuleConfigRepository.findAllByOrderByPriorityAsc()).thenReturn(Collections.emptyList());

        List<RiskRuleResponse> result = riskRuleService.getAllRules();

        assertThat(result).isEmpty();
    }

    // ─── updateRule — happy path ──────────────────────────────────────────────

    @Test
    void shouldUpdateEnabledFlagAndCreateAuditRecord() {
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setEnabled(true);
        request.setChangeReason("Enabling for demo");

        RiskRuleResponse response = RiskRuleResponse.builder()
                .ruleCode("PRICE_DEVIATION_CHECK").enabled(true).build();

        when(riskRuleConfigRepository.findById("PRICE_DEVIATION_CHECK")).thenReturn(Optional.of(sampleRule));
        when(riskRuleConfigRepository.save(any())).thenReturn(sampleRule);
        when(riskRuleMapper.toResponse(sampleRule)).thenReturn(response);

        RiskRuleResponse result = riskRuleService.updateRule("PRICE_DEVIATION_CHECK", request, "admin-user");

        assertThat(result.getEnabled()).isTrue();
        verify(riskRuleAuditRepository).saveAll(argThat(audits -> {
            List<RiskRuleAudit> list = (List<RiskRuleAudit>) audits;
            return list.size() == 1
                    && list.get(0).getFieldChanged().equals("enabled")
                    && list.get(0).getOldValue().equals("false")
                    && list.get(0).getNewValue().equals("true");
        }));
        verify(riskEvaluationService).loadRules();
    }

    @Test
    void shouldReloadRulesAfterSaveEvenWhenNoAuditRecordsCreated() {
        sampleRule.setEnabled(true);
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setEnabled(true);
        request.setChangeReason("No actual change");

        when(riskRuleConfigRepository.findById("PRICE_DEVIATION_CHECK")).thenReturn(Optional.of(sampleRule));
        when(riskRuleConfigRepository.save(any())).thenReturn(sampleRule);
        when(riskRuleMapper.toResponse(sampleRule)).thenReturn(RiskRuleResponse.builder().ruleCode("PRICE_DEVIATION_CHECK").build());

        riskRuleService.updateRule("PRICE_DEVIATION_CHECK", request, "admin-user");

        verify(riskEvaluationService).loadRules();
    }

    @Test
    void shouldUpdateThresholdValueAndCreateAuditRecord() {
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setThresholdValue(new BigDecimal("15.0000"));
        request.setChangeReason("Tightening threshold");

        RiskRuleResponse response = RiskRuleResponse.builder()
                .ruleCode("PRICE_DEVIATION_CHECK").thresholdValue("15.0000").build();

        when(riskRuleConfigRepository.findById("PRICE_DEVIATION_CHECK")).thenReturn(Optional.of(sampleRule));
        when(riskRuleConfigRepository.save(any())).thenReturn(sampleRule);
        when(riskRuleMapper.toResponse(sampleRule)).thenReturn(response);

        riskRuleService.updateRule("PRICE_DEVIATION_CHECK", request, "admin-user");

        verify(riskRuleAuditRepository).saveAll(argThat(audits -> {
            List<RiskRuleAudit> list = (List<RiskRuleAudit>) audits;
            return list.size() == 1 && list.get(0).getFieldChanged().equals("thresholdValue");
        }));
    }

    @Test
    void shouldUpdateBothFieldsAndCreateTwoAuditRecords() {
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setEnabled(true);
        request.setThresholdValue(new BigDecimal("15.0000"));
        request.setChangeReason("Full update for demo");

        when(riskRuleConfigRepository.findById("PRICE_DEVIATION_CHECK")).thenReturn(Optional.of(sampleRule));
        when(riskRuleConfigRepository.save(any())).thenReturn(sampleRule);
        when(riskRuleMapper.toResponse(sampleRule)).thenReturn(RiskRuleResponse.builder().ruleCode("PRICE_DEVIATION_CHECK").build());

        riskRuleService.updateRule("PRICE_DEVIATION_CHECK", request, "admin-user");

        verify(riskRuleAuditRepository).saveAll(argThat(audits -> {
            List<RiskRuleAudit> list = (List<RiskRuleAudit>) audits;
            return list.size() == 2;
        }));
    }

    @Test
    void shouldNotCreateAuditRecordWhenEnabledValueUnchanged() {
        sampleRule.setEnabled(true);
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setEnabled(true);
        request.setChangeReason("No actual change");

        when(riskRuleConfigRepository.findById("PRICE_DEVIATION_CHECK")).thenReturn(Optional.of(sampleRule));
        when(riskRuleConfigRepository.save(any())).thenReturn(sampleRule);
        when(riskRuleMapper.toResponse(sampleRule)).thenReturn(RiskRuleResponse.builder().ruleCode("PRICE_DEVIATION_CHECK").build());

        riskRuleService.updateRule("PRICE_DEVIATION_CHECK", request, "admin-user");

        verify(riskRuleAuditRepository, never()).saveAll(any());
    }

    // ─── updateRule — error paths ─────────────────────────────────────────────

    @Test
    void shouldThrowValidationExceptionWhenChangeReasonIsNull() {
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setEnabled(true);
        request.setChangeReason(null);

        assertThatThrownBy(() -> riskRuleService.updateRule("PRICE_DEVIATION_CHECK", request, "admin-user"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("changeReason is required")
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");

        verifyNoInteractions(riskRuleConfigRepository);
    }

    @Test
    void shouldThrowValidationExceptionWhenChangeReasonIsBlank() {
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setEnabled(true);
        request.setChangeReason("   ");

        assertThatThrownBy(() -> riskRuleService.updateRule("PRICE_DEVIATION_CHECK", request, "admin-user"))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void shouldThrowRuleNotFoundExceptionWhenRuleCodeDoesNotExist() {
        UpdateRiskRuleRequest request = new UpdateRiskRuleRequest();
        request.setEnabled(true);
        request.setChangeReason("Enabling rule");

        when(riskRuleConfigRepository.findById("UNKNOWN_RULE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> riskRuleService.updateRule("UNKNOWN_RULE", request, "admin-user"))
                .isInstanceOf(RuleNotFoundException.class)
                .hasMessageContaining("UNKNOWN_RULE")
                .extracting("errorCode").isEqualTo("RULE_NOT_FOUND");
    }

    // ─── getAuditLog ──────────────────────────────────────────────────────────

    @Test
    void shouldReturnAuditLogOrderedByChangedAtDesc() {
        RiskRuleAudit audit = RiskRuleAudit.builder()
                .id(UUID.randomUUID())
                .ruleCode("PRICE_DEVIATION_CHECK")
                .fieldChanged("enabled")
                .oldValue("false")
                .newValue("true")
                .changedBy("admin-user")
                .changeReason("Demo")
                .changedAt(Instant.now())
                .build();

        RiskRuleAuditResponse auditResponse = RiskRuleAuditResponse.builder()
                .id(audit.getId())
                .ruleCode("PRICE_DEVIATION_CHECK")
                .fieldChanged("enabled")
                .oldValue("false")
                .newValue("true")
                .changedBy("admin-user")
                .changeReason("Demo")
                .changedAt(audit.getChangedAt())
                .build();

        when(riskRuleAuditRepository.findAllByOrderByChangedAtDesc()).thenReturn(List.of(audit));
        when(riskRuleMapper.toAuditResponse(audit)).thenReturn(auditResponse);

        List<RiskRuleAuditResponse> result = riskRuleService.getAuditLog();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFieldChanged()).isEqualTo("enabled");
        assertThat(result.get(0).getChangedBy()).isEqualTo("admin-user");
    }

    @Test
    void shouldReturnEmptyAuditLogWhenNoChanges() {
        when(riskRuleAuditRepository.findAllByOrderByChangedAtDesc()).thenReturn(Collections.emptyList());

        List<RiskRuleAuditResponse> result = riskRuleService.getAuditLog();

        assertThat(result).isEmpty();
    }
}
