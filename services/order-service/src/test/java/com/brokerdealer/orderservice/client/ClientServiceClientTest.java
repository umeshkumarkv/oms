package com.brokerdealer.orderservice.client;

import com.brokerdealer.orderservice.dto.client.ClientResponse;
import com.brokerdealer.orderservice.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ClientServiceClient clientServiceClient;

    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(clientServiceClient, "clientServiceUrl", "http://localhost:8083");
    }

    @Test
    void shouldReturnClientWhenFound() {
        ClientResponse expected = new ClientResponse();
        ClientResponse.ClientData data = new ClientResponse.ClientData();
        data.setStatus("ACTIVE");
        expected.setData(data);

        when(restTemplate.getForEntity(anyString(), eq(ClientResponse.class)))
                .thenReturn(ResponseEntity.ok(expected));

        ClientResponse result = clientServiceClient.getClient(clientId);

        assertThat(result).isNotNull();
        assertThat(result.getData().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldReturnNullWhenClientNotFound() {
        when(restTemplate.getForEntity(anyString(), eq(ClientResponse.class)))
                .thenThrow(HttpClientErrorException.NotFound.class);

        ClientResponse result = clientServiceClient.getClient(clientId);

        assertThat(result).isNull();
    }

    @Test
    void shouldThrowServiceUnavailableOnFallback() {
        assertThatThrownBy(() -> clientServiceClient.getClientFallback(clientId, new RuntimeException("timeout")))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("client-service");
    }
}
