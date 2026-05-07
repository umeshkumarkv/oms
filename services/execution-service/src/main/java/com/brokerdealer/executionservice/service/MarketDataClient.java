package com.brokerdealer.executionservice.service;

import com.brokerdealer.executionservice.dto.response.MarketDataResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketDataClient {

    private final RestTemplate restTemplate;

    @Value("${app.marketdata.base-url:http://localhost:8084}")
    private String marketDataBaseUrl;

    public Optional<MarketDataResponse> getMarketData(String symbol) {
        try {
            String url = marketDataBaseUrl + "/api/v1/market/{symbol}";
            Map<String, ?> response = restTemplate.getForObject(url, Map.class, symbol);
            if (response == null) {
                return Optional.empty();
            }
            Object data = response.get("data");
            if (!(data instanceof Map)) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) data;
            MarketDataResponse result = new MarketDataResponse();
            result.setSymbol(symbol);
            if (dataMap.get("lastPrice") instanceof Number n) {
                result.setLastPrice(new java.math.BigDecimal(n.toString()));
            }
            if (dataMap.get("tradable") instanceof Boolean b) {
                result.setTradable(b);
            }
            if (dataMap.get("halted") instanceof Boolean b) {
                result.setHalted(b);
            }
            return Optional.of(result);
        } catch (Exception ex) {
            log.warn("MarketDataClient failed for symbol={}: {}", symbol, ex.getMessage());
            return Optional.empty();
        }
    }
}
