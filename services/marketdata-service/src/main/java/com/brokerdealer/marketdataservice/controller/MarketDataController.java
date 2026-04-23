package com.brokerdealer.marketdataservice.controller;

import com.brokerdealer.marketdataservice.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataService marketDataService;

    @GetMapping("/{symbol}")
    public ResponseEntity<?> getInstrument(@PathVariable String symbol) {
        // TODO: implement — JIRA story BD-XX
        return ResponseEntity.ok().build();
    }

    @GetMapping("/hours")
    public ResponseEntity<?> getMarketHours() {
        // TODO: implement — JIRA story BD-XX
        return ResponseEntity.ok().build();
    }

    @GetMapping("/instruments")
    public ResponseEntity<?> getInstruments() {
        // TODO: implement — JIRA story BD-XX
        return ResponseEntity.ok().build();
    }
}