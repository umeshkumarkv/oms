package com.brokerdealer.clientservice.filter;

import com.brokerdealer.clientservice.controller.ClientController;
import com.brokerdealer.clientservice.domain.DefaultRoute;
import com.brokerdealer.clientservice.dto.request.UpdateDefaultRouteRequest;
import com.brokerdealer.clientservice.dto.response.ActiveClientResponse;
import com.brokerdealer.clientservice.dto.response.ClientProfileResponse;
import com.brokerdealer.clientservice.dto.response.PortfolioResponse;
import com.brokerdealer.clientservice.dto.response.ReservedCashResponse;
import com.brokerdealer.clientservice.dto.response.UpdateDefaultRouteResponse;
import com.brokerdealer.clientservice.service.ClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientController.class)
class RoleAuthorizationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientService clientService;

    private static final String CLIENT_ID = "550e8400-e29b-41d4-a716-446655440000";

    // ─── Missing header → 401 ────────────────────────────────────────────────

    @Test
    void shouldReturn401WhenRoleHeaderMissingOnGetAll() throws Exception {
        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn401WhenRoleHeaderMissingOnGetById() throws Exception {
        mockMvc.perform(get("/api/v1/clients/" + CLIENT_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ─── VIEWER role ──────────────────────────────────────────────────────────

    @Test
    void shouldAllowViewerToGetAllClients() throws Exception {
        when(clientService.getAllClients()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/clients")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowViewerToGetClientById() throws Exception {
        when(clientService.getClient(any(UUID.class)))
                .thenReturn(ClientProfileResponse.builder().clientId(UUID.fromString(CLIENT_ID)).build());
        mockMvc.perform(get("/api/v1/clients/" + CLIENT_ID)
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowViewerToGetPortfolio() throws Exception {
        when(clientService.getPortfolio(any(UUID.class)))
                .thenReturn(PortfolioResponse.builder().cashBalance("10000.00").reservedCash("0.00")
                        .buyingPower("10000.00").holdings(List.of()).build());
        mockMvc.perform(get("/api/v1/clients/" + CLIENT_ID + "/portfolio")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isOk());
    }

    // ─── TRADER role ──────────────────────────────────────────────────────────

    @Test
    void shouldAllowTraderToGetActiveClients() throws Exception {
        when(clientService.getActiveClients(10)).thenReturn(List.of(
                ActiveClientResponse.builder()
                        .clientId(UUID.fromString(CLIENT_ID))
                        .clientName("Test Corp")
                        .turnoverToday("10000.00")
                        .availableCash("50000.00")
                        .cashLimitWarning(false)
                        .build()));
        mockMvc.perform(get("/api/v1/clients/active")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowTraderToGetClientById() throws Exception {
        when(clientService.getClient(any(UUID.class)))
                .thenReturn(ClientProfileResponse.builder().clientId(UUID.fromString(CLIENT_ID)).build());
        mockMvc.perform(get("/api/v1/clients/" + CLIENT_ID)
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isOk());
    }

    // ─── OPERATIONS role ──────────────────────────────────────────────────────

    @Test
    void shouldAllowOperationsToGetAllClients() throws Exception {
        when(clientService.getAllClients()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/clients")
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isOk());
    }

    // ─── ADMIN role ───────────────────────────────────────────────────────────

    @Test
    void shouldAllowAdminToGetAllClients() throws Exception {
        when(clientService.getAllClients()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/clients")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn403WhenAdminGetsPortfolio() throws Exception {
        mockMvc.perform(get("/api/v1/clients/" + CLIENT_ID + "/portfolio")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    // ─── System call — reserved-cash PATCH bypasses role check ───────────────

    @Test
    void shouldAllowReservedCashPatchWithoutRoleHeader() throws Exception {
        when(clientService.adjustReservedCash(any(UUID.class), any()))
                .thenReturn(ReservedCashResponse.builder()
                        .clientId(UUID.fromString(CLIENT_ID))
                        .reservedCash("1000.00")
                        .buyingPower("9000.00")
                        .build());
        mockMvc.perform(patch("/api/v1/clients/" + CLIENT_ID + "/reserved-cash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adjustment\":\"1000.00\",\"reason\":\"ORDER_RESERVE\"}"))
                .andExpect(status().isOk());
    }

    // ─── PATCH /default-route — ADMIN only ───────────────────────────────────

    @Test
    void shouldAllowAdminToPatchDefaultRoute() throws Exception {
        when(clientService.updateDefaultRoute(any(UUID.class), any(UpdateDefaultRouteRequest.class)))
                .thenReturn(UpdateDefaultRouteResponse.builder()
                        .clientId(UUID.fromString(CLIENT_ID))
                        .defaultRoute(DefaultRoute.NYSE)
                        .build());

        mockMvc.perform(patch("/api/v1/clients/" + CLIENT_ID + "/default-route")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultRoute\":\"NYSE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn403WhenTraderPatchesDefaultRoute() throws Exception {
        mockMvc.perform(patch("/api/v1/clients/" + CLIENT_ID + "/default-route")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultRoute\":\"NYSE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void shouldReturn403WhenOperationsPatchesDefaultRoute() throws Exception {
        mockMvc.perform(patch("/api/v1/clients/" + CLIENT_ID + "/default-route")
                        .header("X-User-Role", "OPERATIONS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultRoute\":\"NYSE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void shouldReturn401WhenNoRoleHeaderOnDefaultRoutePatch() throws Exception {
        mockMvc.perform(patch("/api/v1/clients/" + CLIENT_ID + "/default-route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultRoute\":\"NYSE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ─── /active endpoint role restrictions ───────────────────────────────────

    @Test
    void shouldReturn403WhenViewerGetsActiveClients() throws Exception {
        mockMvc.perform(get("/api/v1/clients/active")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ─── Error envelope shape ─────────────────────────────────────────────────

    @Test
    void shouldReturnStandardErrorEnvelopeOn401() throws Exception {
        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }
}
