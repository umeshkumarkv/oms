package com.brokerdealer.marketdataservice.controller;

import com.brokerdealer.marketdataservice.dto.response.WatchlistEntryResponse;
import com.brokerdealer.marketdataservice.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/watchlists")
@RequiredArgsConstructor
@Slf4j
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getWatchlist(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.debug("GET /api/v1/watchlist: userId={}", userId);
        List<WatchlistEntryResponse> data = watchlistService.getWatchlist(userId);
        return ResponseEntity.ok(buildSuccessResponse(data));
    }

    @PostMapping("/{symbol}")
    public ResponseEntity<Map<String, Object>> addSymbol(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String symbol) {
        log.debug("POST /api/v1/watchlist/{}: userId={}", symbol, userId);
        WatchlistEntryResponse data = watchlistService.addToWatchlist(userId, symbol);
        return ResponseEntity.status(HttpStatus.CREATED).body(buildSuccessResponse(data));
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> removeSymbol(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String symbol) {
        log.debug("DELETE /api/v1/watchlist/{}: userId={}", symbol, userId);
        watchlistService.removeFromWatchlist(userId, symbol);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> buildSuccessResponse(Object data) {
        return Map.of(
                "data", data,
                "meta", Map.of(
                        "timestamp", Instant.now().toString(),
                        "requestId", UUID.randomUUID().toString()
                )
        );
    }
}
