package com.brokerdealer.executionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataClientTest {

    @Mock
    private RestTemplate restTemplate;

    private MarketDataClient marketDataClient;

    @BeforeEach
    void setUp() {
        marketDataClient = new MarketDataClient(restTemplate);
        ReflectionTestUtils.setField(marketDataClient, "marketDataBaseUrl", "http://localhost:8084");
    }

    @Test
    void shouldReturnMarketDataWhenCallSucceeds() {
        Map<String, Object> dataMap = Map.of("lastPrice", 175.50, "tradable", true, "halted", false);
        Map<String, Object> response = Map.of("data", dataMap);
        when(restTemplate.getForObject(anyString(), eq(Map.class), eq("AAPL"))).thenReturn(response);

        Optional<com.brokerdealer.executionservice.dto.response.MarketDataResponse> result =
                marketDataClient.getMarketData("AAPL");

        assertThat(result).isPresent();
        assertThat(result.get().getLastPrice()).isNotNull();
        assertThat(result.get().getSymbol()).isEqualTo("AAPL");
    }

    @Test
    void shouldReturnEmptyWhenCallThrowsException() {
        when(restTemplate.getForObject(anyString(), eq(Map.class), eq("AAPL")))
                .thenThrow(new RestClientException("Connection refused"));

        Optional<com.brokerdealer.executionservice.dto.response.MarketDataResponse> result =
                marketDataClient.getMarketData("AAPL");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenResponseIsNull() {
        when(restTemplate.getForObject(anyString(), eq(Map.class), eq("AAPL"))).thenReturn(null);

        Optional<com.brokerdealer.executionservice.dto.response.MarketDataResponse> result =
                marketDataClient.getMarketData("AAPL");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenDataFieldIsMissing() {
        when(restTemplate.getForObject(anyString(), eq(Map.class), eq("AAPL")))
                .thenReturn(Map.of("status", "ok"));

        Optional<com.brokerdealer.executionservice.dto.response.MarketDataResponse> result =
                marketDataClient.getMarketData("AAPL");

        assertThat(result).isEmpty();
    }
}
