package com.brokerdealer.marketdataservice.controller;

import com.brokerdealer.marketdataservice.config.RoleGuard;
import com.brokerdealer.marketdataservice.dto.response.ScenarioResponse;
import com.brokerdealer.marketdataservice.exception.AccessForbiddenException;
import com.brokerdealer.marketdataservice.exception.GlobalExceptionHandler;
import com.brokerdealer.marketdataservice.exception.ScenarioNotFoundException;
import com.brokerdealer.marketdataservice.exception.SymbolHaltedException;
import com.brokerdealer.marketdataservice.exception.SymbolNotFoundException;
import com.brokerdealer.marketdataservice.service.ScenarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
    private RoleGuard roleGuard;

    private static final String VALID_BODY = """
            {"scenarioId":"SCN-AAPL-RISE-5PCT","symbol":"AAPL"}
            """;

    @Test
    void shouldReturn200WhenAdminStartsScenario() throws Exception {
        ScenarioResponse response = ScenarioResponse.builder()
                .status("SCENARIO_STARTED")
                .scenarioId("SCN-AAPL-RISE-5PCT")
                .symbol("AAPL")
                .expectedDuration("10s")
                .build();
        when(scenarioService.startScenario(any())).thenReturn(response);

        mockMvc.perform(post("/admin/scenario")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCENARIO_STARTED"))
                .andExpect(jsonPath("$.data.scenarioId").value("SCN-AAPL-RISE-5PCT"))
                .andExpect(jsonPath("$.data.symbol").value("AAPL"))
                .andExpect(jsonPath("$.data.expectedDuration").value("10s"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn403WhenRoleIsNotAdmin() throws Exception {
        doThrow(new AccessForbiddenException("FORBIDDEN", "Insufficient role: ADMIN required"))
                .when(roleGuard).requireRole(any(), eq("ADMIN"));

        mockMvc.perform(post("/admin/scenario")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn401WhenRoleHeaderMissing() throws Exception {
        doThrow(new AccessForbiddenException("UNAUTHORIZED", "X-User-Role header is required"))
                .when(roleGuard).requireRole(any(), eq("ADMIN"));

        mockMvc.perform(post("/admin/scenario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn404WhenScenarioIdNotFound() throws Exception {
        when(scenarioService.startScenario(any()))
                .thenThrow(new ScenarioNotFoundException("SCENARIO_NOT_FOUND", "Scenario not found: SCN-UNKNOWN"));

        mockMvc.perform(post("/admin/scenario")
                        .header("X-User-Role", "ADMIN")
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

        mockMvc.perform(post("/admin/scenario")
                        .header("X-User-Role", "ADMIN")
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

        mockMvc.perform(post("/admin/scenario")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenarioId":"SCN-AAPL-RISE-5PCT","symbol":"HALT"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SYMBOL_HALTED"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn400WhenRequestBodyIsInvalid() throws Exception {
        mockMvc.perform(post("/admin/scenario")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenarioId":"","symbol":"AAPL"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
