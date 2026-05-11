package com.brokerdealer.orderservice.client;

import com.brokerdealer.orderservice.dto.market.HolidaysResponse;
import com.brokerdealer.orderservice.dto.market.InstrumentResponse;
import com.brokerdealer.orderservice.dto.market.MarketHoursResponse;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private MarketDataServiceClient marketDataServiceClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(marketDataServiceClient, "marketDataServiceUrl", "http://localhost:8084");
    }

    @Test
    void shouldReturnInstrumentWhenFound() {
        InstrumentResponse expected = new InstrumentResponse();
        InstrumentResponse.InstrumentData data = new InstrumentResponse.InstrumentData();
        data.setSymbol("AAPL");
        data.setTradable(true);
        data.setHalted(false);
        expected.setData(data);

        when(restTemplate.getForEntity(anyString(), eq(InstrumentResponse.class)))
                .thenReturn(ResponseEntity.ok(expected));

        InstrumentResponse result = marketDataServiceClient.getInstrument("AAPL");

        assertThat(result).isNotNull();
        assertThat(result.getData().getSymbol()).isEqualTo("AAPL");
    }

    @Test
    void shouldReturnNullWhenInstrumentNotFound() {
        when(restTemplate.getForEntity(anyString(), eq(InstrumentResponse.class)))
                .thenThrow(HttpClientErrorException.NotFound.class);

        InstrumentResponse result = marketDataServiceClient.getInstrument("BADTICKER");

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnMarketHoursWhenAvailable() {
        MarketHoursResponse expected = new MarketHoursResponse();
        MarketHoursResponse.MarketHoursData data = new MarketHoursResponse.MarketHoursData();
        data.setMarketOpen(true);
        expected.setData(data);

        when(restTemplate.getForEntity(anyString(), eq(MarketHoursResponse.class)))
                .thenReturn(ResponseEntity.ok(expected));

        MarketHoursResponse result = marketDataServiceClient.getMarketHours();

        assertThat(result).isNotNull();
        assertThat(result.getData().getMarketOpen()).isTrue();
    }

    @Test
    void shouldThrowServiceUnavailableOnInstrumentFallback() {
        assertThatThrownBy(() -> marketDataServiceClient.getInstrumentFallback("AAPL", new RuntimeException("timeout")))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("marketdata-service");
    }

    @Test
    void shouldThrowServiceUnavailableOnMarketHoursFallback() {
        assertThatThrownBy(() -> marketDataServiceClient.getMarketHoursFallback(new RuntimeException("timeout")))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("marketdata-service");
    }

    @Test
    void shouldReturnHolidayDatesFromMarketdataService() {
        HolidaysResponse.HolidayItem item = new HolidaysResponse.HolidayItem();
        item.setHolidayDate("2026-01-01");
        item.setHolidayName("New Year's Day");

        HolidaysResponse expected = new HolidaysResponse();
        expected.setData(List.of(item));

        when(restTemplate.getForEntity(anyString(), eq(HolidaysResponse.class)))
                .thenReturn(ResponseEntity.ok(expected));

        List<String> result = marketDataServiceClient.getHolidays();

        assertThat(result).containsExactly("2026-01-01");
    }

    @Test
    void shouldReturnEmptyListWhenHolidaysResponseBodyIsNull() {
        when(restTemplate.getForEntity(anyString(), eq(HolidaysResponse.class)))
                .thenReturn(ResponseEntity.ok(null));

        List<String> result = marketDataServiceClient.getHolidays();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowServiceUnavailableOnHolidaysFallback() {
        assertThatThrownBy(() -> marketDataServiceClient.getHolidaysFallback(new RuntimeException("timeout")))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("marketdata-service");
    }
}
