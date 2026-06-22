package com.brokerdealer.marketdataservice.controller;

import com.brokerdealer.marketdataservice.domain.SearchType;
import com.brokerdealer.marketdataservice.dto.response.InstrumentSearchResponse;
import com.brokerdealer.marketdataservice.exception.GlobalExceptionHandler;
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
class InstrumentSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketDataService marketDataService;

    @MockBean
    private TickService tickService;

    @MockBean
    private MarketDataWebSocketHandler webSocketHandler;

    private static final InstrumentSearchResponse AAPL = InstrumentSearchResponse.builder()
            .symbol("AAPL")
            .cusip("037833100")
            .secNumber("320193")
            .lastPrice("175.00")
            .tradable(true)
            .build();

    @Test
    void shouldReturn200WithMatchingSymbol() throws Exception {
        when(marketDataService.searchInstrumentsByType("AAPL", SearchType.ALL)).thenReturn(List.of(AAPL));

        mockMvc.perform(get("/api/v1/market/instruments/search").param("query", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.data[0].cusip").value("037833100"))
                .andExpect(jsonPath("$.data[0].secNumber").value("320193"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn200WithMatchByCusipType() throws Exception {
        when(marketDataService.searchInstrumentsByType("037833100", SearchType.CUSIP)).thenReturn(List.of(AAPL));

        mockMvc.perform(get("/api/v1/market/instruments/search")
                        .param("query", "037833100")
                        .param("type", "CUSIP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].cusip").value("037833100"));
    }

    @Test
    void shouldReturn200WithMatchBySecNumberType() throws Exception {
        when(marketDataService.searchInstrumentsByType("320193", SearchType.SEC_NUMBER)).thenReturn(List.of(AAPL));

        mockMvc.perform(get("/api/v1/market/instruments/search")
                        .param("query", "320193")
                        .param("type", "SEC_NUMBER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].secNumber").value("320193"));
    }

    @Test
    void shouldReturn200WithEmptyArrayWhenNoMatch() throws Exception {
        when(marketDataService.searchInstrumentsByType("UNKNOWN999", SearchType.ALL)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/market/instruments/search").param("query", "UNKNOWN999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void shouldReturn400WhenQueryParamMissing() throws Exception {
        mockMvc.perform(get("/api/v1/market/instruments/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn200WithSymbolTypeFilter() throws Exception {
        when(marketDataService.searchInstrumentsByType("appl", SearchType.SYMBOL)).thenReturn(List.of(AAPL));

        mockMvc.perform(get("/api/v1/market/instruments/search")
                        .param("query", "appl")
                        .param("type", "SYMBOL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].symbol").value("AAPL"));
    }
}
