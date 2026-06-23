package com.brokerdealer.riskservice.controller;

import com.brokerdealer.riskservice.domain.PendingChangeStatus;
import com.brokerdealer.riskservice.domain.PendingChangeType;
import com.brokerdealer.riskservice.dto.response.PendingChangeResponse;
import com.brokerdealer.riskservice.dto.response.RiskRuleAuditResponse;
import com.brokerdealer.riskservice.dto.response.RiskRuleResponse;
import com.brokerdealer.riskservice.exception.ChangeAlreadyReviewedException;
import com.brokerdealer.riskservice.exception.GlobalExceptionHandler;
import com.brokerdealer.riskservice.exception.PendingChangeNotFoundException;
import com.brokerdealer.riskservice.exception.RuleNotFoundException;
import com.brokerdealer.riskservice.exception.SelfApprovalNotAllowedException;
import com.brokerdealer.riskservice.exception.ValidationException;
import com.brokerdealer.riskservice.service.PendingChangeService;
import com.brokerdealer.riskservice.service.RiskRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RiskRuleController.class)
class RiskRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RiskRuleService riskRuleService;

    @MockBean
    private PendingChangeService pendingChangeService;

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String MAKER_ID = "maker-uuid";
    private static final String CHECKER_ID = "checker-uuid";

    private PendingChangeResponse buildPendingResponse(UUID id, PendingChangeType type, PendingChangeStatus status) {
        return PendingChangeResponse.builder()
                .id(id)
                .ruleCode("SINGLE_ORDER_QTY_LIMIT")
                .changeType(type)
                .proposedEnabled(type == PendingChangeType.DISABLE ? Boolean.FALSE : null)
                .makerUserId(MAKER_ID)
                .makerUsername("admin01")
                .status(status)
                .submittedAt(Instant.now())
                .build();
    }

    // ─── GET /api/v1/risk/rules ───────────────────────────────────────────────

    @Test
    void shouldReturn200WithRuleListOnGetRiskRules() throws Exception {
        RiskRuleResponse rule = RiskRuleResponse.builder()
                .ruleCode("PRICE_DEVIATION_CHECK")
                .ruleName("Price Deviation Check")
                .ruleCategory("FAT_FINGER")
                .enabled(false)
                .thresholdValue("10.0000")
                .thresholdUnit("PERCENT")
                .action("REJECT")
                .priority(3)
                .build();

        when(riskRuleService.getAllRules()).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/v1/risk/rules")
                        .header("X-User-Role", ADMIN_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ruleCode").value("PRICE_DEVIATION_CHECK"))
                .andExpect(jsonPath("$.data[0].enabled").value(false))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn200WithEmptyListWhenNoRules() throws Exception {
        when(riskRuleService.getAllRules()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/risk/rules")
                        .header("X-User-Role", ADMIN_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void shouldReturn200WithAllThirteenRulesWhenSeeded() throws Exception {
        List<RiskRuleResponse> thirteenRules = IntStream.rangeClosed(1, 13)
                .mapToObj(i -> RiskRuleResponse.builder()
                        .ruleCode("RULE_" + i)
                        .ruleName("Rule " + i)
                        .ruleCategory("FINANCIAL_LIMIT")
                        .description("Rule description " + i)
                        .enabled(i <= 2)
                        .action("REJECT")
                        .priority(i)
                        .build())
                .collect(Collectors.toList());

        when(riskRuleService.getAllRules()).thenReturn(thirteenRules);

        mockMvc.perform(get("/api/v1/risk/rules")
                        .header("X-User-Role", ADMIN_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(13))
                .andExpect(jsonPath("$.data[0].ruleCode").value("RULE_1"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[2].enabled").value(false))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    // ─── PATCH /api/v1/risk/rules/{ruleCode} ─────────────────────────────────

    @Test
    void shouldReturn202WithPendingChangeIdWhenSubmitPendingChange() throws Exception {
        UUID changeId = UUID.randomUUID();
        PendingChangeResponse pending = buildPendingResponse(changeId, PendingChangeType.DISABLE, PendingChangeStatus.PENDING);

        when(pendingChangeService.submitPendingChange(eq("SINGLE_ORDER_QTY_LIMIT"), any(), any(), any()))
                .thenReturn(pending);

        String body = """
                {"enabled": false, "changeReason": "Disabling for test"}
                """;

        mockMvc.perform(patch("/api/v1/risk/rules/SINGLE_ORDER_QTY_LIMIT")
                        .header("X-User-Role", ADMIN_ROLE)
                        .header("X-User-Id", MAKER_ID)
                        .header("X-Username", "admin01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(changeId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.changeType").value("DISABLE"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn400WhenNeitherEnabledNorThresholdProvided() throws Exception {
        when(pendingChangeService.submitPendingChange(any(), any(), any(), any()))
                .thenThrow(new ValidationException("VALIDATION_ERROR",
                        "At least one of 'enabled' or 'thresholdValue' must be provided"));

        mockMvc.perform(patch("/api/v1/risk/rules/SINGLE_ORDER_QTY_LIMIT")
                        .header("X-User-Role", ADMIN_ROLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn404WhenRuleCodeUnknownOnPatch() throws Exception {
        when(pendingChangeService.submitPendingChange(eq("UNKNOWN_RULE"), any(), any(), any()))
                .thenThrow(new RuleNotFoundException("RULE_NOT_FOUND", "Risk rule not found: UNKNOWN_RULE"));

        mockMvc.perform(patch("/api/v1/risk/rules/UNKNOWN_RULE")
                        .header("X-User-Role", ADMIN_ROLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RULE_NOT_FOUND"));
    }

    @Test
    void shouldReturn403WhenOperationsRoleAttemptsRulePatch() throws Exception {
        mockMvc.perform(patch("/api/v1/risk/rules/SINGLE_ORDER_QTY_LIMIT")
                        .header("X-User-Role", "OPERATIONS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isForbidden());
    }

    // ─── GET /api/v1/risk/rules/audit ─────────────────────────────────────────

    @Test
    void shouldReturn200WithAuditLogOnGetAudit() throws Exception {
        RiskRuleAuditResponse entry = RiskRuleAuditResponse.builder()
                .id(UUID.randomUUID())
                .ruleCode("PRICE_DEVIATION_CHECK")
                .fieldChanged("enabled")
                .oldValue("false")
                .newValue("true")
                .changedBy("admin-uuid")
                .changeReason("Enabling for demo")
                .changedAt(Instant.now())
                .build();

        when(riskRuleService.getAuditLog()).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/v1/risk/rules/audit")
                        .header("X-User-Role", ADMIN_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ruleCode").value("PRICE_DEVIATION_CHECK"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    // ─── GET /api/v1/risk/rules/pending-changes ───────────────────────────────

    @Test
    void shouldReturn200WithPendingChangesListWhenNoFilter() throws Exception {
        UUID changeId = UUID.randomUUID();
        PendingChangeResponse pending = buildPendingResponse(changeId, PendingChangeType.DISABLE, PendingChangeStatus.PENDING);

        when(pendingChangeService.listPendingChanges(isNull())).thenReturn(List.of(pending));

        mockMvc.perform(get("/api/v1/risk/rules/pending-changes")
                        .header("X-User-Role", ADMIN_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(changeId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn200WithFilteredChangesWhenStatusParamProvided() throws Exception {
        UUID changeId = UUID.randomUUID();
        PendingChangeResponse pending = buildPendingResponse(changeId, PendingChangeType.DISABLE, PendingChangeStatus.PENDING);

        when(pendingChangeService.listPendingChanges("PENDING")).thenReturn(List.of(pending));

        mockMvc.perform(get("/api/v1/risk/rules/pending-changes?status=PENDING")
                        .header("X-User-Role", ADMIN_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void shouldReturn403WhenTraderRoleAccessesPendingChanges() throws Exception {
        mockMvc.perform(get("/api/v1/risk/rules/pending-changes")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isForbidden());
    }

    // ─── GET /api/v1/risk/rules/pending-changes/{changeId} ───────────────────

    @Test
    void shouldReturn200WithSinglePendingChangeById() throws Exception {
        UUID changeId = UUID.randomUUID();
        PendingChangeResponse pending = buildPendingResponse(changeId, PendingChangeType.DISABLE, PendingChangeStatus.PENDING);

        when(pendingChangeService.getPendingChange(changeId)).thenReturn(pending);

        mockMvc.perform(get("/api/v1/risk/rules/pending-changes/" + changeId)
                        .header("X-User-Role", ADMIN_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(changeId.toString()))
                .andExpect(jsonPath("$.data.ruleCode").value("SINGLE_ORDER_QTY_LIMIT"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn404WhenPendingChangeIdUnknown() throws Exception {
        UUID changeId = UUID.randomUUID();
        when(pendingChangeService.getPendingChange(changeId))
                .thenThrow(new PendingChangeNotFoundException("PENDING_CHANGE_NOT_FOUND",
                        "Pending change not found: " + changeId));

        mockMvc.perform(get("/api/v1/risk/rules/pending-changes/" + changeId)
                        .header("X-User-Role", ADMIN_ROLE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PENDING_CHANGE_NOT_FOUND"));
    }

    // ─── PATCH /api/v1/risk/rules/pending-changes/{changeId}/approve ─────────

    @Test
    void shouldReturn200WithApprovedChangeWhenCheckerIsDifferentFromMaker() throws Exception {
        UUID changeId = UUID.randomUUID();
        PendingChangeResponse approved = buildPendingResponse(changeId, PendingChangeType.DISABLE, PendingChangeStatus.APPROVED);

        when(pendingChangeService.approveChange(eq(changeId), eq(CHECKER_ID))).thenReturn(approved);

        mockMvc.perform(patch("/api/v1/risk/rules/pending-changes/" + changeId + "/approve")
                        .header("X-User-Role", ADMIN_ROLE)
                        .header("X-User-Id", CHECKER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn403WhenSameUserAttemptsApproval() throws Exception {
        UUID changeId = UUID.randomUUID();
        when(pendingChangeService.approveChange(eq(changeId), eq(MAKER_ID)))
                .thenThrow(new SelfApprovalNotAllowedException("SELF_APPROVAL_NOT_ALLOWED",
                        "The checker must not be the same user who submitted the change"));

        mockMvc.perform(patch("/api/v1/risk/rules/pending-changes/" + changeId + "/approve")
                        .header("X-User-Role", ADMIN_ROLE)
                        .header("X-User-Id", MAKER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SELF_APPROVAL_NOT_ALLOWED"));
    }

    @Test
    void shouldReturn409WhenApproveOnAlreadyReviewedChange() throws Exception {
        UUID changeId = UUID.randomUUID();
        when(pendingChangeService.approveChange(eq(changeId), any()))
                .thenThrow(new ChangeAlreadyReviewedException("CHANGE_ALREADY_REVIEWED",
                        "Pending change has already been approved"));

        mockMvc.perform(patch("/api/v1/risk/rules/pending-changes/" + changeId + "/approve")
                        .header("X-User-Role", ADMIN_ROLE)
                        .header("X-User-Id", CHECKER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CHANGE_ALREADY_REVIEWED"));
    }

    // ─── PATCH /api/v1/risk/rules/pending-changes/{changeId}/reject ──────────

    @Test
    void shouldReturn200WithRejectedChangeWhenReasonProvided() throws Exception {
        UUID changeId = UUID.randomUUID();
        PendingChangeResponse rejected = buildPendingResponse(changeId, PendingChangeType.DISABLE, PendingChangeStatus.REJECTED);

        when(pendingChangeService.rejectChange(eq(changeId), eq(CHECKER_ID), eq("Not approved this quarter")))
                .thenReturn(rejected);

        String body = """
                {"rejectionReason": "Not approved this quarter"}
                """;

        mockMvc.perform(patch("/api/v1/risk/rules/pending-changes/" + changeId + "/reject")
                        .header("X-User-Role", ADMIN_ROLE)
                        .header("X-User-Id", CHECKER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn400WhenRejectWithEmptyReason() throws Exception {
        UUID changeId = UUID.randomUUID();
        when(pendingChangeService.rejectChange(eq(changeId), any(), eq("")))
                .thenThrow(new ValidationException("VALIDATION_ERROR", "rejectionReason is required when rejecting a change"));

        String body = """
                {"rejectionReason": ""}
                """;

        mockMvc.perform(patch("/api/v1/risk/rules/pending-changes/" + changeId + "/reject")
                        .header("X-User-Role", ADMIN_ROLE)
                        .header("X-User-Id", CHECKER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn409WhenRejectOnAlreadyReviewedChange() throws Exception {
        UUID changeId = UUID.randomUUID();
        when(pendingChangeService.rejectChange(eq(changeId), any(), any()))
                .thenThrow(new ChangeAlreadyReviewedException("CHANGE_ALREADY_REVIEWED",
                        "Pending change has already been rejected"));

        String body = """
                {"rejectionReason": "Too late"}
                """;

        mockMvc.perform(patch("/api/v1/risk/rules/pending-changes/" + changeId + "/reject")
                        .header("X-User-Role", ADMIN_ROLE)
                        .header("X-User-Id", CHECKER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CHANGE_ALREADY_REVIEWED"));
    }
}
