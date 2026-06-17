package com.brokerdealer.orderservice.controller;

import com.brokerdealer.orderservice.dto.response.ResponseMeta;
import com.brokerdealer.orderservice.dto.response.RiskAlertsData;
import com.brokerdealer.orderservice.dto.response.RiskAlertsResponse;
import com.brokerdealer.orderservice.dto.response.TradingSummaryData;
import com.brokerdealer.orderservice.dto.response.TradingSummaryResponse;
import com.brokerdealer.orderservice.exception.GlobalExceptionHandler;
import com.brokerdealer.orderservice.filter.RoleAuthorizationFilter;
import com.brokerdealer.orderservice.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({GlobalExceptionHandler.class, RoleAuthorizationFilter.class})
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @Test
    void shouldReturn200WithRiskAlertsForTraderRole() throws Exception {
        when(dashboardService.getRiskAlerts()).thenReturn(buildRiskAlertsResponse(3, 7, 2, 1));

        mockMvc.perform(get("/api/v1/dashboard/risk-alerts")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientsNearLimit").value(3))
                .andExpect(jsonPath("$.data.cashRejectCount").value(7))
                .andExpect(jsonPath("$.data.largeOrdersAwaitingReview").value(2))
                .andExpect(jsonPath("$.data.pendingVenueAck").value(1));
    }

    @Test
    void shouldReturn200WithRiskAlertsForOperationsRole() throws Exception {
        when(dashboardService.getRiskAlerts()).thenReturn(buildRiskAlertsResponse(1, 2, 3, 4));

        mockMvc.perform(get("/api/v1/dashboard/risk-alerts")
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientsNearLimit").value(1));
    }

    @Test
    void shouldReturn200WithRiskAlertsForAdminRole() throws Exception {
        when(dashboardService.getRiskAlerts()).thenReturn(buildRiskAlertsResponse(0, 0, 0, 0));

        mockMvc.perform(get("/api/v1/dashboard/risk-alerts")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientsNearLimit").value(0));
    }

    @Test
    void shouldReturn200WithAllZeroCountsOnEmptyDay() throws Exception {
        when(dashboardService.getRiskAlerts()).thenReturn(buildRiskAlertsResponse(0, 0, 0, 0));

        mockMvc.perform(get("/api/v1/dashboard/risk-alerts")
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientsNearLimit").value(0))
                .andExpect(jsonPath("$.data.cashRejectCount").value(0))
                .andExpect(jsonPath("$.data.largeOrdersAwaitingReview").value(0))
                .andExpect(jsonPath("$.data.pendingVenueAck").value(0));
    }

    @Test
    void shouldReturn401WhenNoRoleHeaderProvided() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/risk-alerts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn403ForViewerRole() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/risk-alerts")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void shouldReturnStandardEnvelopeWithTimestampAndRequestId() throws Exception {
        when(dashboardService.getRiskAlerts()).thenReturn(buildRiskAlertsResponse(0, 0, 0, 0));

        mockMvc.perform(get("/api/v1/dashboard/risk-alerts")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    private RiskAlertsResponse buildRiskAlertsResponse(int clientsNearLimit, int cashRejectCount,
                                                        int largeOrders, int pendingAck) {
        return RiskAlertsResponse.builder()
                .data(RiskAlertsData.builder()
                        .clientsNearLimit(clientsNearLimit)
                        .cashRejectCount(cashRejectCount)
                        .largeOrdersAwaitingReview(largeOrders)
                        .pendingVenueAck(pendingAck)
                        .build())
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }

    // ── GET /api/v1/dashboard/summary ────────────────────────────────────────

    @Test
    void shouldReturn200WithSummaryEnvelopeForTraderRole() throws Exception {
        when(dashboardService.getTradingSummary()).thenReturn(buildSummaryResponse());

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.turnoverToday").value("1284750.0000"))
                .andExpect(jsonPath("$.data.ordersToday").value(17))
                .andExpect(jsonPath("$.data.fillRate").value("76.47"))
                .andExpect(jsonPath("$.data.buySideNotional").value("892400.0000"))
                .andExpect(jsonPath("$.data.sellSideNotional").value("392350.0000"));
    }

    @Test
    void shouldReturn200WithSummaryEnvelopeForOperationsRole() throws Exception {
        when(dashboardService.getTradingSummary()).thenReturn(buildSummaryResponse());

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.turnoverToday").value("1284750.0000"));
    }

    @Test
    void shouldReturn200WithSummaryEnvelopeForAdminRole() throws Exception {
        when(dashboardService.getTradingSummary()).thenReturn(buildSummaryResponse());

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ordersToday").value(17));
    }

    @Test
    void shouldReturn401WhenRoleHeaderMissingForSummary() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn403ForViewerRoleOnSummary() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void shouldReturnSummaryStandardEnvelopeWithMetaFields() throws Exception {
        when(dashboardService.getTradingSummary()).thenReturn(buildSummaryResponse());

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    private TradingSummaryResponse buildSummaryResponse() {
        return TradingSummaryResponse.builder()
                .data(TradingSummaryData.builder()
                        .turnoverToday("1284750.0000")
                        .ordersToday(17)
                        .fillRate("76.47")
                        .buySideNotional("892400.0000")
                        .sellSideNotional("392350.0000")
                        .build())
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }
}
