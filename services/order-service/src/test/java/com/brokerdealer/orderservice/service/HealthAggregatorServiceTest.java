package com.brokerdealer.orderservice.service;

import com.brokerdealer.orderservice.config.HealthCheckProperties;
import com.brokerdealer.orderservice.domain.ServiceHealthStatus;
import com.brokerdealer.orderservice.dto.response.HealthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthAggregatorServiceTest {

    @Mock
    private RestTemplate healthRestTemplate;

    private HealthAggregatorService healthAggregatorService;

    @BeforeEach
    @SuppressWarnings("rawtypes")
    void setUp() {
        HealthCheckProperties properties = new HealthCheckProperties();
        Map<String, String> services = new LinkedHashMap<>();
        services.put("auth-service", "http://auth-service:8081");
        services.put("order-service", "http://localhost:8082");
        services.put("client-service", "http://client-service:8083");
        services.put("marketdata-service", "http://marketdata-service:8084");
        services.put("risk-service", "http://risk-service:8085");
        services.put("execution-service", "http://execution-service:8086");
        services.put("audit-service", "http://audit-service:8087");
        services.put("notification-service", "http://notification-service:8088");
        properties.setServices(services);
        healthAggregatorService = new HealthAggregatorService(healthRestTemplate, properties);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void shouldReturnUpForAllServicesWhenAllReturn200() {
        ResponseEntity<Map> okResponse = ResponseEntity.ok(Map.of("status", "UP"));
        when(healthRestTemplate.getForEntity(anyString(), eq(Map.class))).thenReturn(okResponse);

        HealthResponse response = healthAggregatorService.getSystemHealth();

        assertThat(response.getData()).hasSize(8);
        assertThat(response.getData()).allMatch(e -> e.getStatus() == ServiceHealthStatus.UP);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void shouldReturnDegradedWhenReadTimeoutOccurs() {
        when(healthRestTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("timeout", new HttpTimeoutException("read timeout")));

        HealthResponse response = healthAggregatorService.getSystemHealth();

        assertThat(response.getData()).hasSize(8);
        assertThat(response.getData()).allMatch(e -> e.getStatus() == ServiceHealthStatus.DEGRADED);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void shouldReturnDownWhenConnectionRefused() {
        when(healthRestTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("refused", new ConnectException("Connection refused")));

        HealthResponse response = healthAggregatorService.getSystemHealth();

        assertThat(response.getData()).hasSize(8);
        assertThat(response.getData()).allMatch(e -> e.getStatus() == ServiceHealthStatus.DOWN);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void shouldReturnDownWhenNon2xxResponse() {
        when(healthRestTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        HealthResponse response = healthAggregatorService.getSystemHealth();

        assertThat(response.getData()).hasSize(8);
        assertThat(response.getData()).allMatch(e -> e.getStatus() == ServiceHealthStatus.DOWN);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void shouldReturnDownWhenUnexpectedExceptionOccurs() {
        when(healthRestTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("unexpected error"));

        HealthResponse response = healthAggregatorService.getSystemHealth();

        assertThat(response.getData()).hasSize(8);
        assertThat(response.getData()).allMatch(e -> e.getStatus() == ServiceHealthStatus.DOWN);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void shouldReturnAllEightEntriesRegardlessOfIndividualFailures() {
        when(healthRestTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(0, String.class);
                    if (url.contains("auth-service") || url.contains("order-service")) {
                        return ResponseEntity.ok(Map.of("status", "UP"));
                    }
                    throw new ResourceAccessException("refused", new ConnectException("Connection refused"));
                });

        HealthResponse response = healthAggregatorService.getSystemHealth();

        assertThat(response.getData()).hasSize(8);
        assertThat(response.getData().stream().anyMatch(e -> e.getStatus() == ServiceHealthStatus.UP)).isTrue();
        assertThat(response.getData().stream().anyMatch(e -> e.getStatus() == ServiceHealthStatus.DOWN)).isTrue();
    }

    @Test
    @SuppressWarnings("rawtypes")
    void shouldReturnResponseEnvelopeWithMetaFields() {
        ResponseEntity<Map> okResponse = ResponseEntity.ok(Map.of("status", "UP"));
        when(healthRestTemplate.getForEntity(anyString(), eq(Map.class))).thenReturn(okResponse);

        HealthResponse response = healthAggregatorService.getSystemHealth();

        assertThat(response.getMeta()).isNotNull();
        assertThat(response.getMeta().getTimestamp()).isNotBlank();
        assertThat(response.getMeta().getRequestId()).isNotBlank();
        assertThat(response.getData()).isNotNull();
    }
}
