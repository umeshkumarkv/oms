package com.brokerdealer.executionservice.filter;

import com.brokerdealer.executionservice.controller.ExecutionController;
import com.brokerdealer.executionservice.service.ExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExecutionController.class)
class RoleAuthorizationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExecutionService executionService;

    // ─── Missing header → 401 ────────────────────────────────────────────────

    @Test
    void shouldReturn401WhenRoleHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/v1/executions/550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ─── VIEWER role ──────────────────────────────────────────────────────────

    @Test
    void shouldAllowViewerToGetExecutions() throws Exception {
        mockMvc.perform(get("/api/v1/executions/550e8400-e29b-41d4-a716-446655440000")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isOk());
    }

    // ─── TRADER role ──────────────────────────────────────────────────────────

    @Test
    void shouldAllowTraderToGetExecutions() throws Exception {
        mockMvc.perform(get("/api/v1/executions/550e8400-e29b-41d4-a716-446655440000")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isOk());
    }

    // ─── OPERATIONS role ──────────────────────────────────────────────────────

    @Test
    void shouldAllowOperationsToGetExecutions() throws Exception {
        mockMvc.perform(get("/api/v1/executions/550e8400-e29b-41d4-a716-446655440000")
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isOk());
    }

    // ─── ADMIN role ───────────────────────────────────────────────────────────

    @Test
    void shouldAllowAdminToGetExecutions() throws Exception {
        mockMvc.perform(get("/api/v1/executions/550e8400-e29b-41d4-a716-446655440000")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    // ─── Unknown role ─────────────────────────────────────────────────────────

    @Test
    void shouldReturn403WhenUnknownRoleRequests() throws Exception {
        mockMvc.perform(get("/api/v1/executions/550e8400-e29b-41d4-a716-446655440000")
                        .header("X-User-Role", "HACKER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
