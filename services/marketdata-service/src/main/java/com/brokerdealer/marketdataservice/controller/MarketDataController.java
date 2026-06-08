package com.brokerdealer.marketdataservice.controller;

import com.brokerdealer.marketdataservice.dto.response.InstrumentResponse;
import com.brokerdealer.marketdataservice.dto.response.InstrumentSearchResponse;
import com.brokerdealer.marketdataservice.dto.response.InstrumentSummaryResponse;
import com.brokerdealer.marketdataservice.dto.response.MarketConfigResponse;
import com.brokerdealer.marketdataservice.dto.response.MarketHolidayResponse;
import com.brokerdealer.marketdataservice.dto.response.MarketHoursResponse;
import com.brokerdealer.marketdataservice.dto.response.MarketMoversResponse;
import com.brokerdealer.marketdataservice.dto.response.TickHistoryResponse;
import com.brokerdealer.marketdataservice.service.MarketDataService;
import com.brokerdealer.marketdataservice.service.TickService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
@Slf4j
public class MarketDataController {

    private final MarketDataService marketDataService;
    private final TickService tickService;

    // --- literal paths first (all take priority over /{symbol} below) ---

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchInstruments(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        log.debug("GET /api/v1/market/search?q={}&limit={}", q, limit);
        List<InstrumentSearchResponse> data = marketDataService.searchInstruments(q, limit);
        return ResponseEntity.ok(buildSuccessResponse(data));
    }

    @GetMapping("/hours")
    public ResponseEntity<Map<String, Object>> getMarketHours() {
        log.debug("GET /api/v1/market/hours");
        MarketHoursResponse data = marketDataService.getMarketHours();
        return ResponseEntity.ok(buildSuccessResponse(data));
    }

    @GetMapping("/holidays")
    public ResponseEntity<Map<String, Object>> getHolidays() {
        log.debug("GET /api/v1/market/holidays");
        List<MarketHolidayResponse> data = marketDataService.getHolidays();
        return ResponseEntity.ok(buildSuccessResponse(data));
    }

    @GetMapping("/instruments")
    public ResponseEntity<Map<String, Object>> getInstruments() {
        log.debug("GET /api/v1/market/instruments");
        List<InstrumentSummaryResponse> data = marketDataService.getInstruments();
        return ResponseEntity.ok(buildSuccessResponse(data));
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getMarketConfig() {
        log.debug("GET /api/v1/market/config");
        MarketConfigResponse data = marketDataService.getMarketConfig();
        return ResponseEntity.ok(buildSuccessResponse(data));
    }

    @GetMapping("/movers")
    public ResponseEntity<Map<String, Object>> getMarketMovers() {
        log.debug("GET /api/v1/market/movers");
        MarketMoversResponse data = marketDataService.getMarketMovers();
        return ResponseEntity.ok(buildSuccessResponse(data));
    }

    @GetMapping("/ticks/history")
    public ResponseEntity<Map<String, Object>> getTickHistory(@RequestParam String symbol) {
        log.debug("GET /api/v1/market/ticks/history?symbol={}", symbol);
        TickHistoryResponse data = tickService.getHistory(symbol);
        return ResponseEntity.ok(buildSuccessResponse(data));
    }

    // --- catch-all path variable — must remain last ---

    @GetMapping("/{symbol}")
    public ResponseEntity<Map<String, Object>> getInstrument(@PathVariable String symbol) {
        log.debug("GET /api/v1/market/{}", symbol);
        InstrumentResponse data = marketDataService.getInstrument(symbol);
        return ResponseEntity.ok(buildSuccessResponse(data));
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
