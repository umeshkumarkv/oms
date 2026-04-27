package com.brokerdealer.clientservice.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketDataClient {

    private final RestTemplate restTemplate;

    @Value("${services.marketdata.url}")
    private String marketDataBaseUrl;

    @CircuitBreaker(name = "marketdata", fallbackMethod = "getLastPriceFallback")
    public BigDecimal getLastPrice(String symbol) {
        String url = marketDataBaseUrl + "/api/v1/market/" + symbol;
        log.debug("Fetching market price: symbol={}", symbol);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response != null && response.get("data") instanceof Map<?, ?> data) {
            Object price = data.get("lastPrice");
            if (price != null) {
                return new BigDecimal(price.toString());
            }
        }
        return null;
    }

    public BigDecimal getLastPriceFallback(String symbol, Exception ex) {
        log.warn("Circuit breaker open for marketdata-service: symbol={}", symbol);
        return null;
    }
}
