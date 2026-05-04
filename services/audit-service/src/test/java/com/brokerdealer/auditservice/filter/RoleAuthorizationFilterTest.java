package com.brokerdealer.auditservice.filter;

import com.brokerdealer.auditservice.controller.AuditController;
import com.brokerdealer.auditservice.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditController.class)
class RoleAuthorizationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditService auditService;

    // ─── Missing header → 401 ────────────────────────────────────────────────

    @Test
    void shouldReturn401WhenRoleHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/v1/audit/trail"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ─── VIEWER role ──────────────────────────────────────────────────────────

    @Test
    void shouldAllowViewerToGetAuditTrail() throws Exception {
        mockMvc.perform(get("/api/v1/audit/trail")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowViewerToGetOrderAudit() throws Exception {
        mockMvc.perform(get("/api/v1/audit/orders/550e8400-e29b-41d4-a716-446655440000")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isOk());
    }

    // ─── TRADER role ──────────────────────────────────────────────────────────

    @Test
    void shouldReturn403WhenTraderGetsAuditTrail() throws Exception {
        mockMvc.perform(get("/api/v1/audit/trail")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ─── OPERATIONS role ──────────────────────────────────────────────────────

    @Test
    void shouldAllowOperationsToGetAuditTrail() throws Exception {
        mockMvc.perform(get("/api/v1/audit/trail")
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isOk());
    }

    // ─── ADMIN role ───────────────────────────────────────────────────────────

    @Test
    void shouldAllowAdminToGetAuditTrail() throws Exception {
        mockMvc.perform(get("/api/v1/audit/trail")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAdminToGetOrderAudit() throws Exception {
        mockMvc.perform(get("/api/v1/audit/orders/550e8400-e29b-41d4-a716-446655440000")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    // ─── Error envelope shape ─────────────────────────────────────────────────

    @Test
    void shouldReturnStandardErrorEnvelopeOn403() throws Exception {
        mockMvc.perform(get("/api/v1/audit/trail")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }
}
