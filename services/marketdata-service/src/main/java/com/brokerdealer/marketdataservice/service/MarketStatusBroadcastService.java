package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.websocket.MarketDataWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketStatusBroadcastService {

    private final MarketDataWebSocketHandler webSocketHandler;

    @Scheduled(cron = "0 30 9 * * MON-FRI", zone = "America/New_York")
    public void broadcastMarketOpen() {
        log.info("Broadcasting MARKET_OPEN to WebSocket clients");
        webSocketHandler.broadcastMarketStatus("MARKET_OPEN", "OPEN");
    }

    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "America/New_York")
    public void broadcastMarketClose() {
        log.info("Broadcasting MARKET_CLOSE to WebSocket clients");
        webSocketHandler.broadcastMarketStatus("MARKET_CLOSE", "CLOSED");
    }
}
