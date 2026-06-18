package com.brokerdealer.orderservice.service;

import com.brokerdealer.orderservice.config.HealthCheckProperties;
import com.brokerdealer.orderservice.domain.ServiceHealthStatus;
import com.brokerdealer.orderservice.dto.response.HealthResponse;
import com.brokerdealer.orderservice.dto.response.ResponseMeta;
import com.brokerdealer.orderservice.dto.response.ServiceHealthEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
@Slf4j
public class HealthAggregatorService {

    private final RestTemplate healthRestTemplate;
    private final HealthCheckProperties healthCheckProperties;

    public HealthAggregatorService(
            @Qualifier("healthRestTemplate") RestTemplate healthRestTemplate,
            HealthCheckProperties healthCheckProperties) {
        this.healthRestTemplate = healthRestTemplate;
        this.healthCheckProperties = healthCheckProperties;
    }

    public HealthResponse getSystemHealth() {
        Map<String, String> serviceUrls = healthCheckProperties.getServices();
        List<ServiceHealthEntry> entries;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ServiceHealthEntry>> futures = serviceUrls.entrySet().stream()
                    .map(entry -> executor.submit(() -> checkService(entry.getKey(), entry.getValue())))
                    .toList();

            entries = futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            log.warn("Unexpected error collecting health check result: {}", e.getMessage());
                            return ServiceHealthEntry.builder()
                                    .service("unknown")
                                    .status(ServiceHealthStatus.DOWN)
                                    .build();
                        }
                    })
                    .toList();
        }

        return HealthResponse.builder()
                .data(entries)
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }

    @SuppressWarnings("rawtypes")
    private ServiceHealthEntry checkService(String serviceName, String baseUrl) {
        try {
            ResponseEntity<Map> response = healthRestTemplate.getForEntity(
                    baseUrl + "/actuator/health", Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Health check UP for service={}", serviceName);
                return ServiceHealthEntry.builder()
                        .service(serviceName)
                        .status(ServiceHealthStatus.UP)
                        .build();
            }
            log.warn("Health check DOWN for service={}: HTTP {}", serviceName, response.getStatusCode());
            return ServiceHealthEntry.builder()
                    .service(serviceName)
                    .status(ServiceHealthStatus.DOWN)
                    .build();
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof ConnectException) {
                log.warn("Health check DOWN for service={}: connection refused", serviceName);
                return ServiceHealthEntry.builder()
                        .service(serviceName)
                        .status(ServiceHealthStatus.DOWN)
                        .build();
            }
            log.warn("Health check DEGRADED for service={}: timeout or I/O error", serviceName);
            return ServiceHealthEntry.builder()
                    .service(serviceName)
                    .status(ServiceHealthStatus.DEGRADED)
                    .build();
        } catch (HttpStatusCodeException e) {
            log.warn("Health check DOWN for service={}: HTTP {}", serviceName, e.getStatusCode());
            return ServiceHealthEntry.builder()
                    .service(serviceName)
                    .status(ServiceHealthStatus.DOWN)
                    .build();
        } catch (Exception e) {
            log.warn("Health check DOWN for service={}: unexpected error={}", serviceName, e.getMessage());
            return ServiceHealthEntry.builder()
                    .service(serviceName)
                    .status(ServiceHealthStatus.DOWN)
                    .build();
        }
    }
}
