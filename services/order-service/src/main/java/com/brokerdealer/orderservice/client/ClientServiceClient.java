package com.brokerdealer.orderservice.client;

import com.brokerdealer.orderservice.dto.client.ClientResponse;
import com.brokerdealer.orderservice.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

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
            ResponseEntity<ClientResponse> response = restTemplate.getForEntity(url, ClientResponse.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    public ClientResponse getClientFallback(UUID clientId, Throwable t) {
        log.warn("Circuit breaker open for client-service: clientId={} reason={}", clientId, t.getMessage());
        throw new ServiceUnavailableException("client-service");
    }
}
