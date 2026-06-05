package com.brokerdealer.marketdataservice.controller;

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
class MarketSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketDataService marketDataService;

    @MockBean
    private TickService tickService;

    @MockBean
    private MarketDataWebSocketHandler webSocketHandler;

    @Test
    void shouldReturn200WithMatchingResultsForValidQuery() throws Exception {
        InstrumentSearchResponse result = InstrumentSearchResponse.builder()
                .symbol("AAPL")
                .companyName("Apple Inc.")
                .exchange("NASDAQ")
                .cusip("037833100")
                .secNumber("AAPL-US")
                .lastPrice("175.00")
                .tradable(true)
                .build();

        when(marketDataService.searchInstruments("AAP", 10)).thenReturn(List.of(result));

        mockMvc.perform(get("/api/v1/market/search").param("q", "AAP").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.data[0].companyName").value("Apple Inc."))
                .andExpect(jsonPath("$.data[0].exchange").value("NASDAQ"))
                .andExpect(jsonPath("$.data[0].cusip").value("037833100"))
                .andExpect(jsonPath("$.data[0].secNumber").value("AAPL-US"))
                .andExpect(jsonPath("$.data[0].lastPrice").value("175.00"))
                .andExpect(jsonPath("$.data[0].tradable").value(true))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn200WithEmptyListWhenNoMatch() throws Exception {
        when(marketDataService.searchInstruments("XYZNOTREAL", 10)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/market/search").param("q", "XYZNOTREAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn200WithDefaultLimitOfTenWhenLimitNotProvided() throws Exception {
        when(marketDataService.searchInstruments("AAPL", 10))
                .thenReturn(List.of(InstrumentSearchResponse.builder()
                        .symbol("AAPL").companyName("Apple Inc.").exchange("NASDAQ")
                        .cusip("037833100").secNumber("AAPL-US").lastPrice("175.00").tradable(true)
                        .build()));

        mockMvc.perform(get("/api/v1/market/search").param("q", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].symbol").value("AAPL"));
    }

    @Test
    void shouldReturn200WithCustomLimit() throws Exception {
        when(marketDataService.searchInstruments("A", 5)).thenReturn(List.of(
                InstrumentSearchResponse.builder().symbol("AAPL").companyName("Apple Inc.").exchange("NASDAQ")
                        .cusip("037833100").secNumber("AAPL-US").lastPrice("175.00").tradable(true).build(),
                InstrumentSearchResponse.builder().symbol("AMZN").companyName("Amazon.com Inc.").exchange("NASDAQ")
                        .cusip("023135106").secNumber("AMZN-US").lastPrice("185.00").tradable(true).build()
        ));

        mockMvc.perform(get("/api/v1/market/search").param("q", "A").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.data[1].symbol").value("AMZN"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn200WithMatchByCompanyName() throws Exception {
        when(marketDataService.searchInstruments("Apple", 10)).thenReturn(List.of(
                InstrumentSearchResponse.builder().symbol("AAPL").companyName("Apple Inc.").exchange("NASDAQ")
                        .cusip("037833100").secNumber("AAPL-US").lastPrice("175.00").tradable(true).build()
        ));

        mockMvc.perform(get("/api/v1/market/search").param("q", "Apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].companyName").value("Apple Inc."));
    }
}
