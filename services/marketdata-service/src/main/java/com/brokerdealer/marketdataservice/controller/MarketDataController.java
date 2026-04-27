package com.brokerdealer.marketdataservice.controller;

import com.brokerdealer.marketdataservice.dto.response.InstrumentResponse;
import com.brokerdealer.marketdataservice.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
@Slf4j
public class MarketDataController {

    private final MarketDataService marketDataService;

    @GetMapping("/{symbol}")
    public ResponseEntity<Map<String, Object>> getInstrument(@PathVariable String symbol) {
        log.debug("GET /api/v1/market/{}", symbol);
        InstrumentResponse data = marketDataService.getInstrument(symbol);
        return ResponseEntity.ok(buildSuccessResponse(data));
    }

    @GetMapping("/hours")
    public ResponseEntity<?> getMarketHours() {
        // TODO: implement — JIRA story SCRUM-22
        return ResponseEntity.ok().build();
    }

    @GetMapping("/instruments")
    public ResponseEntity<?> getInstruments() {
        // TODO: implement — JIRA story SCRUM-22
        return ResponseEntity.ok().build();
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