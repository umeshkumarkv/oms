package com.brokerdealer.marketdataservice.controller;

import com.brokerdealer.marketdataservice.dto.response.WatchlistEntryResponse;
import com.brokerdealer.marketdataservice.exception.AccessForbiddenException;
import com.brokerdealer.marketdataservice.exception.GlobalExceptionHandler;
import com.brokerdealer.marketdataservice.exception.SymbolAlreadyInWatchlistException;
import com.brokerdealer.marketdataservice.exception.SymbolNotFoundException;
import com.brokerdealer.marketdataservice.exception.SymbolNotInWatchlistException;
import com.brokerdealer.marketdataservice.exception.WatchlistLimitExceededException;
import com.brokerdealer.marketdataservice.service.WatchlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistController.class)
@Import(GlobalExceptionHandler.class)
class WatchlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WatchlistService watchlistService;

    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String ADDED_AT = Instant.now().toString();

    // --- GET /api/v1/watchlists ---

    @Test
    void shouldReturn200WithWatchlistEntriesForCurrentUser() throws Exception {
        List<WatchlistEntryResponse> entries = List.of(
                WatchlistEntryResponse.builder().symbol("AAPL").addedAt(ADDED_AT).build(),
                WatchlistEntryResponse.builder().symbol("MSFT").addedAt(ADDED_AT).build()
        );
        when(watchlistService.getWatchlist(USER_ID)).thenReturn(entries);

        mockMvc.perform(get("/api/v1/watchlists")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.data[1].symbol").value("MSFT"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn200WithEmptyListWhenWatchlistIsEmpty() throws Exception {
        when(watchlistService.getWatchlist(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/watchlists")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn401WhenUserIdHeaderMissingOnGet() throws Exception {
        when(watchlistService.getWatchlist(null))
                .thenThrow(new AccessForbiddenException("UNAUTHORIZED", "Authentication required"));

        mockMvc.perform(get("/api/v1/watchlists"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    // --- POST /api/v1/watchlists/{symbol} ---

    @Test
    void shouldReturn201WhenSymbolAddedToWatchlist() throws Exception {
        WatchlistEntryResponse entry = WatchlistEntryResponse.builder()
                .symbol("AAPL").addedAt(ADDED_AT).build();
        when(watchlistService.addToWatchlist(USER_ID, "AAPL")).thenReturn(entry);

        mockMvc.perform(post("/api/v1/watchlists/AAPL")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.symbol").value("AAPL"))
                .andExpect(jsonPath("$.data.addedAt").exists())
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn404WhenSymbolNotFoundOnAdd() throws Exception {
        when(watchlistService.addToWatchlist(USER_ID, "UNKNOWN"))
                .thenThrow(new SymbolNotFoundException("SYMBOL_NOT_FOUND", "Symbol not found: UNKNOWN"));

        mockMvc.perform(post("/api/v1/watchlists/UNKNOWN")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SYMBOL_NOT_FOUND"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn409WhenSymbolAlreadyInWatchlist() throws Exception {
        when(watchlistService.addToWatchlist(USER_ID, "AAPL"))
                .thenThrow(new SymbolAlreadyInWatchlistException("SYMBOL_ALREADY_IN_WATCHLIST",
                        "Symbol is already in watchlist: AAPL"));

        mockMvc.perform(post("/api/v1/watchlists/AAPL")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SYMBOL_ALREADY_IN_WATCHLIST"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    void shouldReturn422WhenWatchlistLimitExceeded() throws Exception {
        when(watchlistService.addToWatchlist(USER_ID, "NVDA"))
                .thenThrow(new WatchlistLimitExceededException("WATCHLIST_LIMIT_EXCEEDED",
                        "Watchlist limit of 50 symbols reached"));

        mockMvc.perform(post("/api/v1/watchlists/NVDA")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("WATCHLIST_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    // --- DELETE /api/v1/watchlists/{symbol} ---

    @Test
    void shouldReturn204WhenSymbolRemovedFromWatchlist() throws Exception {
        mockMvc.perform(delete("/api/v1/watchlists/AAPL")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturn404WhenSymbolNotInWatchlistOnRemove() throws Exception {
        doThrow(new SymbolNotInWatchlistException("SYMBOL_NOT_IN_WATCHLIST",
                "Symbol is not in watchlist: TSLA"))
                .when(watchlistService).removeFromWatchlist(USER_ID, "TSLA");

        mockMvc.perform(delete("/api/v1/watchlists/TSLA")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SYMBOL_NOT_IN_WATCHLIST"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }
}
