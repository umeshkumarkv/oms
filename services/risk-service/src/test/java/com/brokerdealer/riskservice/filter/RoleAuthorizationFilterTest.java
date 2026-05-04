package com.brokerdealer.riskservice.filter;

import com.brokerdealer.riskservice.controller.RiskRuleController;
import com.brokerdealer.riskservice.service.RiskRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RiskRuleController.class)
class RoleAuthorizationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RiskRuleService riskRuleService;

    // ─── Missing header → 401 ────────────────────────────────────────────────

    @Test
    void shouldReturn401WhenRoleHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/v1/risk/rules"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ─── VIEWER role ──────────────────────────────────────────────────────────

    @Test
    void shouldReturn403WhenViewerGetsRiskRules() throws Exception {
        mockMvc.perform(get("/api/v1/risk/rules")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ─── TRADER role ──────────────────────────────────────────────────────────

    @Test
    void shouldReturn403WhenTraderGetsRiskRules() throws Exception {
        mockMvc.perform(get("/api/v1/risk/rules")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void shouldReturn403WhenTraderPatchesRiskRule() throws Exception {
        mockMvc.perform(patch("/api/v1/risk/rules/SINGLE_ORDER_QTY_LIMIT")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ─── OPERATIONS role ──────────────────────────────────────────────────────

    @Test
    void shouldReturn403WhenOperationsGetsRiskRules() throws Exception {
        mockMvc.perform(get("/api/v1/risk/rules")
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ─── ADMIN role ───────────────────────────────────────────────────────────

    @Test
    void shouldAllowAdminToGetRiskRules() throws Exception {
        mockMvc.perform(get("/api/v1/risk/rules")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAdminToPatchRiskRule() throws Exception {
        mockMvc.perform(patch("/api/v1/risk/rules/SINGLE_ORDER_QTY_LIMIT")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
    }

    // ─── Error envelope shape ─────────────────────────────────────────────────

    @Test
    void shouldReturnStandardErrorEnvelopeOn403() throws Exception {
        mockMvc.perform(get("/api/v1/risk/rules")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }
}
