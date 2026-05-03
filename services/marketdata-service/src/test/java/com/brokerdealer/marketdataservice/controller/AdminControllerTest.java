package com.brokerdealer.marketdataservice.controller;

import com.brokerdealer.marketdataservice.config.RoleGuard;
import com.brokerdealer.marketdataservice.dto.response.ResetResponse;
import com.brokerdealer.marketdataservice.dto.response.ScenarioResponse;
import com.brokerdealer.marketdataservice.exception.AccessForbiddenException;
import com.brokerdealer.marketdataservice.exception.GlobalExceptionHandler;
import com.brokerdealer.marketdataservice.exception.ScenarioAlreadyActiveException;
import com.brokerdealer.marketdataservice.exception.ValidationException;
import com.brokerdealer.marketdataservice.exception.ScenarioNotFoundException;
import com.brokerdealer.marketdataservice.exception.SymbolHaltedException;
import com.brokerdealer.marketdataservice.exception.SymbolNotFoundException;
import com.brokerdealer.marketdataservice.service.AdminResetService;
import com.brokerdealer.marketdataservice.service.ScenarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(GlobalExceptionHandler.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScenarioService scenarioService;

    @MockBean
    private AdminResetService adminResetService;

    @MockBean
    private RoleGuard roleGuard;

    private static final String VALID_SCENARIO_BODY = """
            {"scenarioId":"SCN-AAPL-RISE-5PCT","symbol":"AAPL"}
            """;

    // =========================================================
    // POST /api/v1/admin/scenario
    // =========================================================

    @Test
    void shouldReturn200WhenAdminStartsScenario() throws Exception {
        ScenarioResponse response = ScenarioResponse.builder()
                .status("SCENARIO_STARTED")
                .scenarioId("SCN-AAPL-RISE-5PCT")
                .symbol("AAPL")
                .expectedDuration("10s")
                .build();
        when(scenarioService.startScenario(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/scenario")
                        .header("Authorization", "Bearer valid-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SCENARIO_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCENARIO_STARTED"))
                .andExpect(jsonPath("$.data.scenarioId").value("SCN-AAPL-RISE-5PCT"))
                .andExpect(jsonPath("$.data.symbol").value("AAPL"))
                .andExpect(jsonPath("$.data.expectedDuration").value("10s"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn409WhenScenarioAlreadyActiveForSymbol() throws Exception {
        when(scenarioService.startScenario(any()))
                .thenThrow(new ScenarioAlreadyActiveException("SCENARIO_ALREADY_ACTIVE",
                        "A scenario is already active for symbol: AAPL"));

        mockMvc.perform(post("/api/v1/admin/scenario")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SCENARIO_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_ALREADY_ACTIVE"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn403WhenRoleIsNotAdmin() throws Exception {
        doThrow(new AccessForbiddenException("FORBIDDEN", "Insufficient role: ADMIN required"))
                .when(roleGuard).requireRole(any(), eq("ADMIN"));

        mockMvc.perform(post("/api/v1/admin/scenario")
                        .header("Authorization", "Bearer trader-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SCENARIO_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn401WhenRoleHeaderMissing() throws Exception {
        doThrow(new AccessForbiddenException("UNAUTHORIZED", "X-User-Role header is required"))
                .when(roleGuard).requireRole(any(), eq("ADMIN"));

        mockMvc.perform(post("/api/v1/admin/scenario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SCENARIO_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn404WhenScenarioIdNotFound() throws Exception {
        when(scenarioService.startScenario(any()))
                .thenThrow(new ScenarioNotFoundException("SCENARIO_NOT_FOUND", "Scenario not found: SCN-UNKNOWN"));

        mockMvc.perform(post("/api/v1/admin/scenario")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenarioId":"SCN-UNKNOWN","symbol":"AAPL"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_FOUND"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn404WhenSymbolNotFound() throws Exception {
        when(scenarioService.startScenario(any()))
                .thenThrow(new SymbolNotFoundException("SYMBOL_NOT_FOUND", "Symbol not found: ZZZZ"));

        mockMvc.perform(post("/api/v1/admin/scenario")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenarioId":"SCN-AAPL-RISE-5PCT","symbol":"ZZZZ"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SYMBOL_NOT_FOUND"));
    }

    @Test
    void shouldReturn422WhenSymbolIsHalted() throws Exception {
        when(scenarioService.startScenario(any()))
                .thenThrow(new SymbolHaltedException("SYMBOL_HALTED",
                        "Symbol is halted and cannot have a scenario triggered: HALT"));

        mockMvc.perform(post("/api/v1/admin/scenario")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenarioId":"SCN-AAPL-RISE-5PCT","symbol":"HALT"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SYMBOL_HALTED"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn400WhenScenarioRequestBodyIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/admin/scenario")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenarioId":"","symbol":"AAPL"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // POST /api/v1/admin/reset
    // =========================================================

    @Test
    void shouldReturn200WhenResetCompletedSuccessfully() throws Exception {
        ResetResponse response = ResetResponse.builder()
                .status("RESET_COMPLETE")
                .timestamp(Instant.now().toString())
                .build();
        when(adminResetService.reset(true)).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/reset")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmReset":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESET_COMPLETE"))
                .andExpect(jsonPath("$.data.timestamp").exists())
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn400WhenConfirmResetIsFalse() throws Exception {
        doThrow(new ValidationException("RESET_NOT_CONFIRMED",
                "confirmReset must be true to proceed with market data reset"))
                .when(adminResetService).reset(false);

        mockMvc.perform(post("/api/v1/admin/reset")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmReset":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RESET_NOT_CONFIRMED"));
    }

    @Test
    void shouldReturn400WhenConfirmResetIsAbsent() throws Exception {
        doThrow(new ValidationException("RESET_NOT_CONFIRMED",
                "confirmReset must be true to proceed with market data reset"))
                .when(adminResetService).reset(null);

        mockMvc.perform(post("/api/v1/admin/reset")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RESET_NOT_CONFIRMED"));
    }

    @Test
    void shouldReturn403WhenResetCalledByNonAdmin() throws Exception {
        doThrow(new AccessForbiddenException("FORBIDDEN", "Insufficient role: ADMIN required"))
                .when(roleGuard).requireRole(any(), eq("ADMIN"));

        mockMvc.perform(post("/api/v1/admin/reset")
                        .header("Authorization", "Bearer trader-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmReset":true}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
