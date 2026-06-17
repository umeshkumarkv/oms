package com.brokerdealer.marketdataservice.filter;

import com.brokerdealer.marketdataservice.config.RoleGuard;
import com.brokerdealer.marketdataservice.controller.MarketDataController;
import com.brokerdealer.marketdataservice.dto.response.InstrumentResponse;
import com.brokerdealer.marketdataservice.dto.response.MarketHoursResponse;
import com.brokerdealer.marketdataservice.exception.GlobalExceptionHandler;
import com.brokerdealer.marketdataservice.service.MarketDataService;
import com.brokerdealer.marketdataservice.service.TickService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MarketDataController.class)
@Import({GlobalExceptionHandler.class, RoleAuthorizationFilter.class})
class RoleAuthorizationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketDataService marketDataService;

    @MockBean
    private TickService tickService;

    @MockBean
    private RoleGuard roleGuard;

    // ─── No role header = internal system call → pass through ────────────────

    @Test
    void shouldAllowRequestWithoutRoleHeaderForInternalCall() throws Exception {
        when(marketDataService.getMarketHours()).thenReturn(
                MarketHoursResponse.builder()
                        .marketOpen(true).openTime("09:30:00").closeTime("16:00:00")
                        .timezone("America/New_York").nextOpenDate(null).build());

        mockMvc.perform(get("/api/v1/market/hours"))
                .andExpect(status().isOk());
    }

    // ─── ADMIN role → 403 on all market GET endpoints ─────────────────────────

    @Test
    void shouldReturn403WhenAdminGetsInstrument() throws Exception {
        mockMvc.perform(get("/api/v1/market/instrument/AAPL")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn403WhenAdminGetsMarketHours() throws Exception {
        mockMvc.perform(get("/api/v1/market/hours")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void shouldReturn403WhenAdminGetsHolidays() throws Exception {
        mockMvc.perform(get("/api/v1/market/holidays")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void shouldReturn403WhenAdminGetsInstruments() throws Exception {
        mockMvc.perform(get("/api/v1/market/instruments")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ─── VIEWER role → 200 ────────────────────────────────────────────────────

    @Test
    void shouldAllowViewerToGetMarketHours() throws Exception {
        when(marketDataService.getMarketHours()).thenReturn(
                MarketHoursResponse.builder()
                        .marketOpen(true).openTime("09:30:00").closeTime("16:00:00")
                        .timezone("America/New_York").nextOpenDate(null).build());

        mockMvc.perform(get("/api/v1/market/hours")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowViewerToGetInstrument() throws Exception {
        when(marketDataService.getInstrument("AAPL")).thenReturn(
                InstrumentResponse.builder()
                        .symbol("AAPL").bidPrice("174.95").askPrice("175.05").lastPrice("175.00")
                        .tradable(true).halted(false).minQuantity(1).maxQuantity(10000)
                        .dataTimestamp("2026-04-27T06:00:00Z").build());

        mockMvc.perform(get("/api/v1/market/instrument/AAPL")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isOk());
    }

    // ─── TRADER role → 200 ────────────────────────────────────────────────────

    @Test
    void shouldAllowTraderToGetMarketHours() throws Exception {
        when(marketDataService.getMarketHours()).thenReturn(
                MarketHoursResponse.builder()
                        .marketOpen(true).openTime("09:30:00").closeTime("16:00:00")
                        .timezone("America/New_York").nextOpenDate(null).build());

        mockMvc.perform(get("/api/v1/market/hours")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isOk());
    }

    // ─── OPERATIONS role → 200 ───────────────────────────────────────────────

    @Test
    void shouldAllowOperationsToGetInstrument() throws Exception {
        when(marketDataService.getInstrument("AAPL")).thenReturn(
                InstrumentResponse.builder()
                        .symbol("AAPL").bidPrice("174.95").askPrice("175.05").lastPrice("175.00")
                        .tradable(true).halted(false).minQuantity(1).maxQuantity(10000)
                        .dataTimestamp("2026-04-27T06:00:00Z").build());

        mockMvc.perform(get("/api/v1/market/instrument/AAPL")
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isOk());
    }
}
