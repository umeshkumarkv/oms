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
        String url = clientServiceUrl + "/api/v1/clients/" + clientId;
        log.debug("Calling client-service: GET {}", url);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Role", "SYSTEM");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<ClientResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, ClientResponse.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    public ClientResponse getClientFallback(UUID clientId, Throwable t) {
        log.warn("Circuit breaker open for client-service: clientId={} reason={}", clientId, t.getMessage());
        throw new ServiceUnavailableException("client-service");
    }

    @CircuitBreaker(name = "client-service", fallbackMethod = "reserveCashFallback")
    public void reserveCash(UUID clientId, BigDecimal orderValue) {
        String url = clientServiceUrl + "/api/v1/clients/" + clientId + "/reserved-cash";
        ReserveCashRequest body = ReserveCashRequest.builder()
                .adjustment(orderValue.toPlainString())
                .reason("BUY order reservation")
                .build();
        log.debug("Calling client-service: PATCH {} adjustment={}", url, orderValue.toPlainString());
        try {
            restTemplate.patchForObject(url, body, Void.class);
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            throw new InsufficientBuyingPowerException();
        }
    }

    public void reserveCashFallback(UUID clientId, BigDecimal orderValue, Throwable t) {
        log.warn("Circuit breaker open for client-service reserveCash: clientId={} reason={}", clientId, t.getMessage());
        throw new ServiceUnavailableException("client-service");
    }
}
