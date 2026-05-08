package com.brokerdealer.riskservice.controller;

import com.brokerdealer.riskservice.dto.response.RiskRuleAuditResponse;
import com.brokerdealer.riskservice.dto.response.RiskRuleResponse;
import com.brokerdealer.riskservice.exception.GlobalExceptionHandler;
import com.brokerdealer.riskservice.exception.RuleNotFoundException;
import com.brokerdealer.riskservice.exception.ValidationException;
import com.brokerdealer.riskservice.service.RiskRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private static final String ADMIN_ROLE = "ADMIN";

    // ─── GET /api/v1/risk/rules ───────────────────────────────────────────────

    @Test
    void shouldReturn200WithRuleListOnGetRiskRules() throws Exception {
        RiskRuleResponse rule = RiskRuleResponse.builder()
                .ruleCode("PRICE_DEVIATION_CHECK")
                .ruleName("Price Deviation Check")
                .ruleCategory("FAT_FINGER")
                .enabled(false)
                .thresholdValue(new BigDecimal("10.0000"))
                .thresholdUnit("PERCENT")
                .action("REJECT")
                .priority(3)
                .build();

        when(riskRuleService.getAllRules()).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/v1/risk/rules")
                        .header("X-User-Role", ADMIN_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ruleCode").value("PRICE_DEVIATION_CHECK"))
                .andExpect(jsonPath("$.data[0].ruleName").value("Price Deviation Check"))
                .andExpect(jsonPath("$.data[0].ruleCategory").value("FAT_FINGER"))
                .andExpect(jsonPath("$.data[0].enabled").value(false))
                .andExpect(jsonPath("$.data[0].action").value("REJECT"))
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

    // ─── PATCH /api/v1/risk/rules/{ruleCode} ─────────────────────────────────

    @Test
    void shouldReturn200WithUpdatedRuleOnPatch() throws Exception {
        RiskRuleResponse updated = RiskRuleResponse.builder()
                .ruleCode("PRICE_DEVIATION_CHECK")
                .ruleName("Price Deviation Check")
                .ruleCategory("FAT_FINGER")
                .enabled(true)
                .thresholdValue(new BigDecimal("10.0000"))
                .thresholdUnit("PERCENT")
                .action("REJECT")
                .priority(3)
                .build();

        when(riskRuleService.updateRule(eq("PRICE_DEVIATION_CHECK"), any(), any())).thenReturn(updated);

        String body = """
                {"enabled": true, "changeReason": "Enabling for demo"}
                """;

        mockMvc.perform(patch("/api/v1/risk/rules/PRICE_DEVIATION_CHECK")
                        .header("X-User-Role", ADMIN_ROLE)
                        .header("X-User-Id", "admin-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ruleCode").value("PRICE_DEVIATION_CHECK"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn400WhenChangeReasonMissing() throws Exception {
        when(riskRuleService.updateRule(any(), any(), any()))
                .thenThrow(new ValidationException("VALIDATION_ERROR", "changeReason is required"));

        String body = """
                {"enabled": true}
                """;

        mockMvc.perform(patch("/api/v1/risk/rules/PRICE_DEVIATION_CHECK")
                        .header("X-User-Role", ADMIN_ROLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("changeReason is required"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn404WhenRuleCodeUnknown() throws Exception {
        when(riskRuleService.updateRule(eq("UNKNOWN_RULE"), any(), any()))
                .thenThrow(new RuleNotFoundException("RULE_NOT_FOUND", "Risk rule not found: UNKNOWN_RULE"));

        String body = """
                {"enabled": true, "changeReason": "Test"}
                """;

        mockMvc.perform(patch("/api/v1/risk/rules/UNKNOWN_RULE")
                        .header("X-User-Role", ADMIN_ROLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RULE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Risk rule not found: UNKNOWN_RULE"));
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
                .andExpect(jsonPath("$.data[0].fieldChanged").value("enabled"))
                .andExpect(jsonPath("$.data[0].oldValue").value("false"))
                .andExpect(jsonPath("$.data[0].newValue").value("true"))
                .andExpect(jsonPath("$.data[0].changedBy").value("admin-uuid"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn200WithEmptyAuditLog() throws Exception {
        when(riskRuleService.getAuditLog()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/risk/rules/audit")
                        .header("X-User-Role", ADMIN_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
