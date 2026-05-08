package com.brokerdealer.orderservice.client;

import com.brokerdealer.orderservice.dto.execution.ExecutionItem;
import com.brokerdealer.orderservice.dto.execution.ExecutionServiceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ExecutionServiceClient executionServiceClient;

    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(executionServiceClient, "executionServiceUrl", "http://localhost:8086");
    }

    @Test
    void shouldReturnFillsWhenExecutionServiceResponds() {
        ExecutionItem item = new ExecutionItem();
        item.setExecutionId("ex-1");
        item.setExecutedQty(10);
        item.setExecutedPrice("150.00");

        ExecutionServiceResponse response = new ExecutionServiceResponse();
        response.setData(List.of(item));

        when(restTemplate.getForObject(
                eq("http://localhost:8086/api/v1/executions/" + orderId),
                eq(ExecutionServiceResponse.class)))
                .thenReturn(response);

        List<ExecutionItem> fills = executionServiceClient.getFills(orderId);

        assertThat(fills).hasSize(1);
        assertThat(fills.get(0).getExecutionId()).isEqualTo("ex-1");
        assertThat(fills.get(0).getExecutedQty()).isEqualTo(10);
    }

    @Test
    void shouldReturnEmptyListWhenExecutionServiceReturnsNull() {
        when(restTemplate.getForObject(anyString(), eq(ExecutionServiceResponse.class)))
                .thenReturn(null);

        List<ExecutionItem> fills = executionServiceClient.getFills(orderId);

        assertThat(fills).isEmpty();
    }

    @Test
    void shouldReturnEmptyListOnFallback() {
        List<ExecutionItem> fills = executionServiceClient.getFillsFallback(orderId, new RuntimeException("timeout"));

        assertThat(fills).isEmpty();
    }
}
