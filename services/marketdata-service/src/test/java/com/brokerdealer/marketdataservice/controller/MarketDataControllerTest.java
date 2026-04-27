package com.brokerdealer.marketdataservice.controller;

import com.brokerdealer.marketdataservice.dto.response.InstrumentResponse;
import com.brokerdealer.marketdataservice.exception.GlobalExceptionHandler;
import com.brokerdealer.marketdataservice.exception.SymbolNotFoundException;
import com.brokerdealer.marketdataservice.service.MarketDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    void getInstrument_validSymbol_returns200() throws Exception {
        InstrumentResponse response = InstrumentResponse.builder()
                .symbol("AAPL")
                .bidPrice("174.95")
                .askPrice("175.05")
                .lastPrice("175.00")
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
                .andExpect(jsonPath("$.data.tradable").value(true))
                .andExpect(jsonPath("$.data.halted").value(false))
                .andExpect(jsonPath("$.data.minQuantity").value(1))
                .andExpect(jsonPath("$.data.maxQuantity").value(10000))
                .andExpect(jsonPath("$.data.dataTimestamp").value("2026-04-27T06:00:00Z"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void getInstrument_haltedSymbol_returns200WithHaltedTrue() throws Exception {
        InstrumentResponse response = InstrumentResponse.builder()
                .symbol("HALT")
                .bidPrice("49.90")
                .askPrice("50.10")
                .lastPrice("50.00")
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
    void getInstrument_unknownSymbol_returns404WithSymbolNotFound() throws Exception {
        when(marketDataService.getInstrument("UNKNOWN"))
                .thenThrow(new SymbolNotFoundException("SYMBOL_NOT_FOUND", "Symbol not found: UNKNOWN"));

        mockMvc.perform(get("/api/v1/market/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SYMBOL_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Symbol not found: UNKNOWN"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }
}
