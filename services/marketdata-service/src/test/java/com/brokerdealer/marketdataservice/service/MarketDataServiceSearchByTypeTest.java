package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.domain.SearchType;
import com.brokerdealer.marketdataservice.dto.response.InstrumentSearchResponse;
import com.brokerdealer.marketdataservice.mapper.InstrumentMapper;
import com.brokerdealer.marketdataservice.mapper.InstrumentSearchMapper;
import com.brokerdealer.marketdataservice.mapper.InstrumentSummaryMapper;
import com.brokerdealer.marketdataservice.mapper.MarketHolidayMapper;
import com.brokerdealer.marketdataservice.repository.InstrumentRepository;
import com.brokerdealer.marketdataservice.repository.MarketHolidayRepository;
import com.brokerdealer.marketdataservice.repository.MarketIndexRepository;
import com.brokerdealer.marketdataservice.websocket.MarketDataWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceSearchByTypeTest {

    @Mock private InstrumentRepository instrumentRepository;
    @Mock private InstrumentMapper instrumentMapper;
    @Mock private InstrumentSummaryMapper instrumentSummaryMapper;
    @Mock private InstrumentSearchMapper instrumentSearchMapper;
    @Mock private MarketHolidayRepository marketHolidayRepository;
    @Mock private MarketHolidayMapper marketHolidayMapper;
    @Mock private MarketIndexRepository marketIndexRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private MarketDataWebSocketHandler webSocketHandler;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks
    private MarketDataService marketDataService;

    private Instrument aapl;
    private InstrumentSearchResponse aaplResponse;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(marketDataService, "openTime", "09:30");
        ReflectionTestUtils.setField(marketDataService, "closeTime", "16:00");
        ReflectionTestUtils.setField(marketDataService, "timezone", "America/New_York");
        ReflectionTestUtils.setField(marketDataService, "bypassMarketHours", false);
        ReflectionTestUtils.setField(marketDataService, "pollingIntervalMs", 5000L);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        aapl = Instrument.builder()
                .symbol("AAPL")
                .cusip("037833100")
                .secNumber("320193")
                .lastPrice(new BigDecimal("175.00"))
                .tradable(true)
                .tradingHalted(false)
                .build();

        aaplResponse = InstrumentSearchResponse.builder()
                .symbol("AAPL")
                .cusip("037833100")
                .secNumber("320193")
                .lastPrice("175.00")
                .tradable(true)
                .build();
    }

    @Test
    void shouldReturnResultsWhenTypeIsAllAndSymbolMatches() {
        when(instrumentRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of(aapl));
        when(instrumentSearchMapper.toSearchResponse(aapl)).thenReturn(aaplResponse);

        List<InstrumentSearchResponse> results = marketDataService.searchInstrumentsByType("AAPL", SearchType.ALL);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSymbol()).isEqualTo("AAPL");
    }

    @Test
    void shouldReturnResultsWhenTypeIsAllAndCusipMatches() {
        when(instrumentRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of(aapl));
        when(instrumentSearchMapper.toSearchResponse(aapl)).thenReturn(aaplResponse);

        List<InstrumentSearchResponse> results = marketDataService.searchInstrumentsByType("037833100", SearchType.ALL);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCusip()).isEqualTo("037833100");
    }

    @Test
    void shouldReturnResultsWhenTypeIsAllAndSecNumberMatches() {
        when(instrumentRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of(aapl));
        when(instrumentSearchMapper.toSearchResponse(aapl)).thenReturn(aaplResponse);

        List<InstrumentSearchResponse> results = marketDataService.searchInstrumentsByType("320193", SearchType.ALL);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSecNumber()).isEqualTo("320193");
    }

    @Test
    void shouldReturnResultsWhenTypeIsSymbol() {
        when(instrumentRepository.findBySymbolContainingIgnoreCaseOrderBySymbolAsc("appl"))
                .thenReturn(List.of(aapl));
        when(instrumentSearchMapper.toSearchResponse(aapl)).thenReturn(aaplResponse);

        List<InstrumentSearchResponse> results = marketDataService.searchInstrumentsByType("appl", SearchType.SYMBOL);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSymbol()).isEqualTo("AAPL");
    }

    @Test
    void shouldReturnResultsWhenTypeIsCusip() {
        when(instrumentRepository.findByCusipIgnoreCaseOrderBySymbolAsc("037833100"))
                .thenReturn(List.of(aapl));
        when(instrumentSearchMapper.toSearchResponse(aapl)).thenReturn(aaplResponse);

        List<InstrumentSearchResponse> results = marketDataService.searchInstrumentsByType("037833100", SearchType.CUSIP);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCusip()).isEqualTo("037833100");
    }

    @Test
    void shouldReturnResultsWhenTypeIsSecNumber() {
        when(instrumentRepository.findBySecNumberIgnoreCaseOrderBySymbolAsc("320193"))
                .thenReturn(List.of(aapl));
        when(instrumentSearchMapper.toSearchResponse(aapl)).thenReturn(aaplResponse);

        List<InstrumentSearchResponse> results = marketDataService.searchInstrumentsByType("320193", SearchType.SEC_NUMBER);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSecNumber()).isEqualTo("320193");
    }

    @Test
    void shouldReturnEmptyListWhenNoMatch() {
        when(instrumentRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of(aapl));

        List<InstrumentSearchResponse> results = marketDataService.searchInstrumentsByType("UNKNOWN999", SearchType.ALL);

        assertThat(results).isEmpty();
    }

    @Test
    void shouldReturnCachedResultsOnSecondCall() {
        List<InstrumentSearchResponse> cached = List.of(aaplResponse);
        when(valueOps.get("market:instrument:search:aapl:ALL")).thenReturn(cached);

        List<InstrumentSearchResponse> results = marketDataService.searchInstrumentsByType("AAPL", SearchType.ALL);

        assertThat(results).hasSize(1);
        verify(instrumentRepository, never()).findAllByOrderBySymbolAsc();
    }

    @Test
    void shouldPopulateCacheAfterDbQuery() {
        when(instrumentRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of(aapl));
        when(instrumentSearchMapper.toSearchResponse(aapl)).thenReturn(aaplResponse);

        marketDataService.searchInstrumentsByType("AAPL", SearchType.ALL);

        verify(valueOps).set(eq("market:instrument:search:aapl:ALL"), any(), any());
    }
}
