package com.brokerdealer.clientservice.controller;

import com.brokerdealer.clientservice.domain.AccountType;
import com.brokerdealer.clientservice.domain.ClientStatus;
import com.brokerdealer.clientservice.domain.DefaultRoute;
import com.brokerdealer.clientservice.domain.KycStatus;
import com.brokerdealer.clientservice.dto.request.UpdateDefaultRouteRequest;
import com.brokerdealer.clientservice.dto.response.ActiveClientResponse;
import com.brokerdealer.clientservice.dto.response.ClientProfileResponse;
import com.brokerdealer.clientservice.dto.response.HoldingDetailResponse;
import com.brokerdealer.clientservice.dto.response.PortfolioResponse;
import com.brokerdealer.clientservice.dto.response.UpdateDefaultRouteResponse;
import com.brokerdealer.clientservice.exception.ClientNotFoundException;
import com.brokerdealer.clientservice.exception.ValidationException;
import com.brokerdealer.clientservice.service.ClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientController.class)
class ClientControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ClientService clientService;

    private static final UUID CLIENT_001 = UUID.fromString("a1000000-0000-0000-0000-000000000001");

    @Test
    void shouldReturn200WithClientProfileForValidId() throws Exception {
        ClientProfileResponse response = ClientProfileResponse.builder()
                .clientId(CLIENT_001)
                .name("John Smith")
                .accountType(AccountType.CASH)
                .status(ClientStatus.ACTIVE)
                .tradingEnabled(true)
                .kycStatus(KycStatus.VERIFIED)
                .kycExpiryDate(LocalDate.of(2027, 12, 31))
                .build();

        when(clientService.getClient(CLIENT_001)).thenReturn(response);

        mockMvc.perform(get("/api/v1/clients/{id}", CLIENT_001)
                        .header("X-User-Role", "TRADER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientId").value(CLIENT_001.toString()))
                .andExpect(jsonPath("$.data.name").value("John Smith"))
                .andExpect(jsonPath("$.data.accountType").value("CASH"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.tradingEnabled").value(true))
                .andExpect(jsonPath("$.data.kycStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn404WithClientNotFoundErrorForUnknownId() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(clientService.getClient(unknownId))
                .thenThrow(new ClientNotFoundException("CLIENT_NOT_FOUND",
                        "Client not found: " + unknownId));

        mockMvc.perform(get("/api/v1/clients/{id}", unknownId)
                        .header("X-User-Role", "TRADER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CLIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturnTradingDisabledForClient003() throws Exception {
        UUID client003 = UUID.fromString("a3000000-0000-0000-0000-000000000003");
        ClientProfileResponse response = ClientProfileResponse.builder()
                .clientId(client003)
                .name("Mike Davis")
                .accountType(AccountType.CASH)
                .status(ClientStatus.ACTIVE)
                .tradingEnabled(false)
                .kycStatus(KycStatus.VERIFIED)
                .kycExpiryDate(LocalDate.of(2027, 12, 31))
                .build();

        when(clientService.getClient(client003)).thenReturn(response);

        mockMvc.perform(get("/api/v1/clients/{id}", client003)
                        .header("X-User-Role", "TRADER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tradingEnabled").value(false));
    }

    @Test
    void shouldReturnBlockedStatusForClient004() throws Exception {
        UUID client004 = UUID.fromString("a4000000-0000-0000-0000-000000000004");
        ClientProfileResponse response = ClientProfileResponse.builder()
                .clientId(client004)
                .name("Emily Chen")
                .accountType(AccountType.CASH)
                .status(ClientStatus.BLOCKED)
                .tradingEnabled(true)
                .kycStatus(KycStatus.VERIFIED)
                .kycExpiryDate(LocalDate.of(2027, 12, 31))
                .build();

        when(clientService.getClient(client004)).thenReturn(response);

        mockMvc.perform(get("/api/v1/clients/{id}", client004)
                        .header("X-User-Role", "TRADER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"));
    }

    @Test
    void shouldReturnExpiredKycForClient005() throws Exception {
        UUID client005 = UUID.fromString("a5000000-0000-0000-0000-000000000005");
        ClientProfileResponse response = ClientProfileResponse.builder()
                .clientId(client005)
                .name("Robert Wilson")
                .accountType(AccountType.CASH)
                .status(ClientStatus.ACTIVE)
                .tradingEnabled(true)
                .kycStatus(KycStatus.EXPIRED)
                .kycExpiryDate(LocalDate.of(2025, 1, 1))
                .build();

        when(clientService.getClient(client005)).thenReturn(response);

        mockMvc.perform(get("/api/v1/clients/{id}", client005)
                        .header("X-User-Role", "TRADER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycStatus").value("EXPIRED"));
    }

    @Test
    void shouldReturn200WithPortfolioForValidClient() throws Exception {
        HoldingDetailResponse holding = HoldingDetailResponse.builder()
                .symbol("AAPL")
                .quantity(500)
                .avgPrice("168.4000")
                .currentPrice("185.00")
                .marketValue("92500.00")
                .unrealisedPnL("8300.00")
                .build();
        PortfolioResponse portfolio = PortfolioResponse.builder()
                .cashBalance("100000.00")
                .holdings(List.of(holding))
                .build();

        when(clientService.getPortfolio(CLIENT_001)).thenReturn(portfolio);

        mockMvc.perform(get("/api/v1/clients/{id}/portfolio", CLIENT_001)
                        .header("X-User-Role", "TRADER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cashBalance").value("100000.00"))
                .andExpect(jsonPath("$.data.holdings[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.data.holdings[0].quantity").value(500))
                .andExpect(jsonPath("$.data.holdings[0].currentPrice").value("185.00"))
                .andExpect(jsonPath("$.data.holdings[0].marketValue").value("92500.00"))
                .andExpect(jsonPath("$.data.holdings[0].unrealisedPnL").value("8300.00"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn200WithEmptyHoldingsForClientWithNoPositions() throws Exception {
        UUID client005 = UUID.fromString("a5000000-0000-0000-0000-000000000005");
        PortfolioResponse portfolio = PortfolioResponse.builder()
                .cashBalance("50000.00")
                .holdings(Collections.emptyList())
                .build();

        when(clientService.getPortfolio(client005)).thenReturn(portfolio);

        mockMvc.perform(get("/api/v1/clients/{id}/portfolio", client005)
                        .header("X-User-Role", "TRADER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holdings").isArray())
                .andExpect(jsonPath("$.data.holdings").isEmpty());
    }

    @Test
    void shouldReturn200WithNullPriceFieldsWhenMarketDataUnavailable() throws Exception {
        HoldingDetailResponse holding = HoldingDetailResponse.builder()
                .symbol("AAPL")
                .quantity(500)
                .avgPrice("168.4000")
                .currentPrice(null)
                .marketValue(null)
                .unrealisedPnL(null)
                .build();
        PortfolioResponse portfolio = PortfolioResponse.builder()
                .cashBalance("100000.00")
                .holdings(List.of(holding))
                .build();

        when(clientService.getPortfolio(CLIENT_001)).thenReturn(portfolio);

        mockMvc.perform(get("/api/v1/clients/{id}/portfolio", CLIENT_001)
                        .header("X-User-Role", "TRADER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holdings[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.data.holdings[0].currentPrice").doesNotExist());
    }

    @Test
    void shouldReturn404WithClientNotFoundOnPortfolioForUnknownId() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(clientService.getPortfolio(unknownId))
                .thenThrow(new ClientNotFoundException("CLIENT_NOT_FOUND",
                        "Client not found: " + unknownId));

        mockMvc.perform(get("/api/v1/clients/{id}/portfolio", unknownId)
                        .header("X-User-Role", "TRADER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CLIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn200WithUpdatedDefaultRouteOnValidPatch() throws Exception {
        UpdateDefaultRouteResponse response = UpdateDefaultRouteResponse.builder()
                .clientId(CLIENT_001)
                .defaultRoute(DefaultRoute.NYSE)
                .build();

        when(clientService.updateDefaultRoute(eq(CLIENT_001), any(UpdateDefaultRouteRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/clients/{id}/default-route", CLIENT_001)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"defaultRoute\":\"NYSE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientId").value(CLIENT_001.toString()))
                .andExpect(jsonPath("$.data.defaultRoute").value("NYSE"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn400WithInvalidRouteErrorForUnknownRouteValue() throws Exception {
        when(clientService.updateDefaultRoute(eq(CLIENT_001), any(UpdateDefaultRouteRequest.class)))
                .thenThrow(new ValidationException("INVALID_ROUTE",
                        "Invalid route value: LONDON. Valid values: NASDAQ, NYSE, ARCA, BATS"));

        mockMvc.perform(patch("/api/v1/clients/{id}/default-route", CLIENT_001)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"defaultRoute\":\"LONDON\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ROUTE"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn404WhenClientNotFoundOnDefaultRouteUpdate() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(clientService.updateDefaultRoute(eq(unknownId), any(UpdateDefaultRouteRequest.class)))
                .thenThrow(new ClientNotFoundException("CLIENT_NOT_FOUND",
                        "Client not found: " + unknownId));

        mockMvc.perform(patch("/api/v1/clients/{id}/default-route", unknownId)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"defaultRoute\":\"NYSE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CLIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    // --- /active endpoint tests ---

    @Test
    void shouldReturn200WithActiveClientsForOperationsRole() throws Exception {
        List<ActiveClientResponse> response = List.of(
                ActiveClientResponse.builder()
                        .clientId(CLIENT_001)
                        .clientName("Alice Corp")
                        .turnoverToday("50000.00")
                        .availableCash("95000.00")
                        .cashLimitWarning(false)
                        .build());
        when(clientService.getActiveClients(10)).thenReturn(response);

        mockMvc.perform(get("/api/v1/clients/active")
                        .header("X-User-Role", "OPERATIONS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].clientId").value(CLIENT_001.toString()))
                .andExpect(jsonPath("$.data[0].clientName").value("Alice Corp"))
                .andExpect(jsonPath("$.data[0].turnoverToday").value("50000.00"))
                .andExpect(jsonPath("$.data[0].availableCash").value("95000.00"))
                .andExpect(jsonPath("$.data[0].cashLimitWarning").value(false))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn200WithActiveClientsForTraderRole() throws Exception {
        List<ActiveClientResponse> response = List.of(
                ActiveClientResponse.builder()
                        .clientId(CLIENT_001)
                        .clientName("Alice Corp")
                        .turnoverToday("50000.00")
                        .availableCash("95000.00")
                        .cashLimitWarning(true)
                        .build());
        when(clientService.getActiveClients(10)).thenReturn(response);

        mockMvc.perform(get("/api/v1/clients/active")
                        .header("X-User-Role", "TRADER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].cashLimitWarning").value(true))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn200WithActiveClientsForAdminRole() throws Exception {
        when(clientService.getActiveClients(5)).thenReturn(List.of(
                ActiveClientResponse.builder()
                        .clientId(CLIENT_001)
                        .clientName("Alice Corp")
                        .turnoverToday("50000.00")
                        .availableCash("95000.00")
                        .cashLimitWarning(false)
                        .build()));

        mockMvc.perform(get("/api/v1/clients/active")
                        .param("limit", "5")
                        .header("X-User-Role", "ADMIN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void shouldReturn200WithEmptyArrayWhenNoActiveClients() throws Exception {
        when(clientService.getActiveClients(10)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/clients/active")
                        .header("X-User-Role", "OPERATIONS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void shouldReturn400WhenLimitIsZero() throws Exception {
        when(clientService.getActiveClients(0))
                .thenThrow(new ValidationException("VALIDATION_ERROR", "limit must be between 1 and 50"));

        mockMvc.perform(get("/api/v1/clients/active")
                        .param("limit", "0")
                        .header("X-User-Role", "OPERATIONS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn400WhenLimitExceedsFifty() throws Exception {
        when(clientService.getActiveClients(51))
                .thenThrow(new ValidationException("VALIDATION_ERROR", "limit must be between 1 and 50"));

        mockMvc.perform(get("/api/v1/clients/active")
                        .param("limit", "51")
                        .header("X-User-Role", "OPERATIONS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn200WithDefaultLimitWhenLimitNotProvided() throws Exception {
        when(clientService.getActiveClients(10)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/clients/active")
                        .header("X-User-Role", "OPERATIONS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void shouldReturn404WhenPatchingRemovedReservedCashEndpoint() throws Exception {
        mockMvc.perform(patch("/api/v1/clients/{id}/reserved-cash", CLIENT_001)
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adjustment\":\"500.00\",\"reason\":\"test\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ENDPOINT_NOT_FOUND"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }
}
