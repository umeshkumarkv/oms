package com.brokerdealer.orderservice.client;

import com.brokerdealer.orderservice.dto.client.ClientResponse;
import com.brokerdealer.orderservice.dto.client.ReserveCashRequest;
import com.brokerdealer.orderservice.exception.InsufficientBuyingPowerException;
import com.brokerdealer.orderservice.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClientServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.services.client-service.url}")
    private String clientServiceUrl;

    @CircuitBreaker(name = "client-service", fallbackMethod = "getClientFallback")
    public ClientResponse getClient(UUID clientId) {
        String url = clientServiceUrl + "/api/v1/clients/" + clientId + "/portfolio";
        log.debug("Calling client-service: GET {}", url);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Role", "SYSTEM");
            ResponseEntity<ClientResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), ClientResponse.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    public ClientResponse getClientFallback(UUID clientId, Throwable t) {
        log.warn("Circuit breaker open for client-service: clientId={} reason={}", clientId, t.getMessage());
        throw new ServiceUnavailableException("client-service");
    }

    // Returns false when client-service returns 422 (insufficient buying power).
    // Caller must throw InsufficientBuyingPowerException — keeping business errors
    // outside the circuit breaker scope so they never count as circuit failures.
    @CircuitBreaker(name = "client-service", fallbackMethod = "reserveCashFallback")
    public boolean reserveCash(UUID clientId, BigDecimal orderValue) {
        String url = clientServiceUrl + "/api/v1/clients/" + clientId + "/reserved-cash";
        ReserveCashRequest body = ReserveCashRequest.builder()
                .adjustment(orderValue.toPlainString())
                .reason("BUY order reservation")
                .build();
        log.debug("Calling client-service: PATCH {} adjustment={}", url, orderValue.toPlainString());
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Role", "SYSTEM");
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.exchange(url, HttpMethod.PATCH, new HttpEntity<>(body, headers), Void.class);
            return true;
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            return false;
        }
    }

    public boolean reserveCashFallback(UUID clientId, BigDecimal orderValue, Throwable t) {
        log.warn("Circuit breaker open for client-service reserveCash: clientId={} reason={}", clientId, t.getMessage());
        throw new ServiceUnavailableException("client-service");
    }
}
