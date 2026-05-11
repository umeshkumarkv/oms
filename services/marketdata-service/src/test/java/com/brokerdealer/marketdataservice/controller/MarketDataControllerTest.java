package com.brokerdealer.marketdataservice.controller;

import com.brokerdealer.marketdataservice.dto.response.InstrumentResponse;
import com.brokerdealer.marketdataservice.dto.response.InstrumentSummaryResponse;
import com.brokerdealer.marketdataservice.dto.response.MarketHolidayResponse;
import com.brokerdealer.marketdataservice.dto.response.MarketHoursResponse;
import com.brokerdealer.marketdataservice.exception.GlobalExceptionHandler;
import com.brokerdealer.marketdataservice.exception.SymbolNotFoundException;
import com.brokerdealer.marketdataservice.service.MarketDataService;
import com.brokerdealer.marketdataservice.service.TickService;
import com.brokerdealer.marketdataservice.websocket.MarketDataWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MarketDataController.class)
@Import(GlobalExceptionHandler.class)
class MarketDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketDataService marketDataService;

    @MockBean
    private TickService tickService;

    @MockBean
    private MarketDataWebSocketHandler webSocketHandler;

    @Test
    void shouldReturn200WithAllRequiredFieldsWhenSymbolIsValid() throws Exception {
        InstrumentResponse response = InstrumentResponse.builder()
                .symbol("AAPL")
                .bidPrice("174.95")
                .askPrice("175.05")
                .lastPrice("175.00")
                .change("1.25")
                .changePct("0.71")
                .volume(4521000L)
                .dayHigh("176.10")
                .dayLow("174.80")
                .previousClose("173.75")
                .marketStatus("OPEN")
                .tradable(true)
                .halted(false)
                .minQuantity(1)
                .maxQuantity(10000)
                .dataTimestamp("2026-04-27T06:00:00Z")
                .build();

        when(marketDataService.getInstrument("AAPL")).thenReturn(response);

        mockMvc.perform(get("/api/v1/market/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbol").value("AAPL"))
                .andExpect(jsonPath("$.data.bidPrice").value("174.95"))
                .andExpect(jsonPath("$.data.askPrice").value("175.05"))
                .andExpect(jsonPath("$.data.lastPrice").value("175.00"))
                .andExpect(jsonPath("$.data.change").value("1.25"))
                .andExpect(jsonPath("$.data.changePct").value("0.71"))
                .andExpect(jsonPath("$.data.volume").value(4521000))
                .andExpect(jsonPath("$.data.dayHigh").value("176.10"))
                .andExpect(jsonPath("$.data.dayLow").value("174.80"))
                .andExpect(jsonPath("$.data.previousClose").value("173.75"))
                .andExpect(jsonPath("$.data.marketStatus").value("OPEN"))
                .andExpect(jsonPath("$.data.tradable").value(true))
                .andExpect(jsonPath("$.data.halted").value(false))
                .andExpect(jsonPath("$.data.minQuantity").value(1))
                .andExpect(jsonPath("$.data.maxQuantity").value(10000))
                .andExpect(jsonPath("$.data.dataTimestamp").value("2026-04-27T06:00:00Z"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn200WhenSymbolIsLowercase() throws Exception {
        InstrumentResponse response = InstrumentResponse.builder()
                .symbol("AAPL")
                .bidPrice("174.95")
                .askPrice("175.05")
                .lastPrice("175.00")
                .change("0")
                .changePct("0")
                .marketStatus("OPEN")
                .tradable(true)
                .halted(false)
                .minQuantity(1)
                .maxQuantity(10000)
                .dataTimestamp("2026-04-27T06:00:00Z")
                .build();

        when(marketDataService.getInstrument("aapl")).thenReturn(response);

        mockMvc.perform(get("/api/v1/market/aapl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbol").value("AAPL"));
    }

    @Test
    void shouldReturn200WithHaltedTrueWhenSymbolIsHalted() throws Exception {
        InstrumentResponse response = InstrumentResponse.builder()
                .symbol("HALT")
                .bidPrice("49.90")
                .askPrice("50.10")
                .lastPrice("50.00")
                .change("0")
                .changePct("0")
                .marketStatus("OPEN")
                .tradable(true)
                .halted(true)
                .minQuantity(1)
                .maxQuantity(10000)
                .dataTimestamp("2026-04-27T06:00:00Z")
                .build();

        when(marketDataService.getInstrument("HALT")).thenReturn(response);

        mockMvc.perform(get("/api/v1/market/HALT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbol").value("HALT"))
                .andExpect(jsonPath("$.data.tradable").value(true))
                .andExpect(jsonPath("$.data.halted").value(true));
    }

    @Test
    void shouldReturn404WithSymbolNotFoundWhenSymbolIsUnknown() throws Exception {
        when(marketDataService.getInstrument("UNKNOWN"))
                .thenThrow(new SymbolNotFoundException("SYMBOL_NOT_FOUND", "Symbol not found: UNKNOWN"));

        mockMvc.perform(get("/api/v1/market/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SYMBOL_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Symbol not found: UNKNOWN"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn200WithMarketHoursData() throws Exception {
        MarketHoursResponse response = MarketHoursResponse.builder()
                .marketOpen(true)
                .openTime("09:30:00")
                .closeTime("16:00:00")
                .timezone("America/New_York")
                .nextOpenDate(null)
                .build();

        when(marketDataService.getMarketHours()).thenReturn(response);

        mockMvc.perform(get("/api/v1/market/hours"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.marketOpen").value(true))
                .andExpect(jsonPath("$.data.openTime").value("09:30:00"))
                .andExpect(jsonPath("$.data.closeTime").value("16:00:00"))
                .andExpect(jsonPath("$.data.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn200WithHolidayList() throws Exception {
        List<MarketHolidayResponse> holidays = List.of(
                MarketHolidayResponse.builder().holidayDate("2026-01-01").holidayName("New Year's Day").build(),
                MarketHolidayResponse.builder().holidayDate("2026-12-25").holidayName("Christmas Day").build()
        );

        when(marketDataService.getHolidays()).thenReturn(holidays);

        mockMvc.perform(get("/api/v1/market/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].holidayDate").value("2026-01-01"))
                .andExpect(jsonPath("$.data[0].holidayName").value("New Year's Day"))
                .andExpect(jsonPath("$.data[1].holidayDate").value("2026-12-25"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn200WithInstrumentSummaryListIncludingNewFields() throws Exception {
        List<InstrumentSummaryResponse> instruments = List.of(
                InstrumentSummaryResponse.builder()
                        .symbol("AAPL").bidPrice("174.95").askPrice("175.05")
                        .lastPrice("175.00").change("1.25").changePct("0.71")
                        .volume(4521000L).dayHigh("176.10").dayLow("174.80")
                        .previousClose("173.75").marketStatus("OPEN")
                        .tradable(true).halted(false).build(),
                InstrumentSummaryResponse.builder()
                        .symbol("HALT").bidPrice("49.90").askPrice("50.10")
                        .lastPrice("50.00").change("0").changePct("0")
                        .marketStatus("OPEN").tradable(true).halted(true).build()
        );

        when(marketDataService.getInstruments()).thenReturn(instruments);

        mockMvc.perform(get("/api/v1/market/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.data[0].lastPrice").value("175.00"))
                .andExpect(jsonPath("$.data[0].change").value("1.25"))
                .andExpect(jsonPath("$.data[0].changePct").value("0.71"))
                .andExpect(jsonPath("$.data[0].volume").value(4521000))
                .andExpect(jsonPath("$.data[0].dayHigh").value("176.10"))
                .andExpect(jsonPath("$.data[0].dayLow").value("174.80"))
                .andExpect(jsonPath("$.data[0].previousClose").value("173.75"))
                .andExpect(jsonPath("$.data[0].marketStatus").value("OPEN"))
                .andExpect(jsonPath("$.data[0].tradable").value(true))
                .andExpect(jsonPath("$.data[0].halted").value(false))
                .andExpect(jsonPath("$.data[1].symbol").value("HALT"))
                .andExpect(jsonPath("$.data[1].halted").value(true))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }
}
