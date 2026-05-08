package com.brokerdealer.orderservice.client;

import com.brokerdealer.orderservice.dto.execution.ExecutionItem;
import com.brokerdealer.orderservice.dto.execution.ExecutionServiceResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.services.execution-service.url}")
    private String executionServiceUrl;

    @CircuitBreaker(name = "execution-service", fallbackMethod = "getFillsFallback")
    public List<ExecutionItem> getFills(UUID orderId) {
        String url = executionServiceUrl + "/api/v1/executions/" + orderId;
        log.debug("Calling execution-service: GET {}", url);
        ExecutionServiceResponse response = restTemplate.getForObject(url, ExecutionServiceResponse.class);
        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }
        return response.getData();
    }

    public List<ExecutionItem> getFillsFallback(UUID orderId, Throwable t) {
        log.warn("Circuit breaker open for execution-service: orderId={} reason={}", orderId, t.getMessage());
        return Collections.emptyList();
    }
}
