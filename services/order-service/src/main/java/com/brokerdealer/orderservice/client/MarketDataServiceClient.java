package com.brokerdealer.orderservice.client;

import com.brokerdealer.orderservice.dto.market.InstrumentResponse;
import com.brokerdealer.orderservice.dto.market.MarketHoursResponse;
import com.brokerdealer.orderservice.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketDataServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.services.marketdata-service.url}")
    private String marketDataServiceUrl;

    @CircuitBreaker(name = "marketdata-service", fallbackMethod = "getInstrumentFallback")
    public InstrumentResponse getInstrument(String symbol) {
        String url = marketDataServiceUrl + "/api/v1/market/" + symbol;
        log.debug("Calling marketdata-service: GET {}", url);
        try {
            ResponseEntity<InstrumentResponse> response = restTemplate.getForEntity(url, InstrumentResponse.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    public InstrumentResponse getInstrumentFallback(String symbol, Throwable t) {
        log.warn("Circuit breaker open for marketdata-service: symbol={} reason={}", symbol, t.getMessage());
        throw new ServiceUnavailableException("marketdata-service");
    }

    @CircuitBreaker(name = "marketdata-service", fallbackMethod = "getMarketHoursFallback")
    public MarketHoursResponse getMarketHours() {
        String url = marketDataServiceUrl + "/api/v1/market/hours";
        log.debug("Calling marketdata-service: GET {}", url);
        ResponseEntity<MarketHoursResponse> response = restTemplate.getForEntity(url, MarketHoursResponse.class);
        return response.getBody();
    }

    public MarketHoursResponse getMarketHoursFallback(Throwable t) {
        log.warn("Circuit breaker open for marketdata-service (hours): reason={}", t.getMessage());
        throw new ServiceUnavailableException("marketdata-service");
    }
}
