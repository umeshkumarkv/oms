package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.repository.InstrumentRepository;
import com.brokerdealer.marketdataservice.websocket.MarketDataWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceTickServiceTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private MarketDataService marketDataService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private MarketDataWebSocketHandler webSocketHandler;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ScenarioService scenarioService;

    @Mock
    private PriceHistoryStore priceHistoryStore;

    @InjectMocks
    private PriceTickService priceTickService;

    // --- tickInstrument ---

    @Test
    void shouldUpdatePriceWithinBoundsOnTick() {
        Instrument instrument = buildInstrument("AAPL", new BigDecimal("175.00"));

        priceTickService.tickInstrument(instrument, false);

        BigDecimal newPrice = instrument.getLastPrice();
        assertThat(newPrice).isGreaterThanOrEqualTo(new BigDecimal("0.01"));
        assertThat(newPrice).isGreaterThanOrEqualTo(new BigDecimal("174.00"));
        assertThat(newPrice).isLessThanOrEqualTo(new BigDecimal("176.00"));
    }

    @Test
    void shouldNeverTickBelowMinimumPrice() {
        Instrument instrument = buildInstrument("CHEAP", new BigDecimal("0.01"));

        for (int i = 0; i < 100; i++) {
            priceTickService.tickInstrument(instrument, false);
            assertThat(instrument.getLastPrice())
                    .isGreaterThanOrEqualTo(new BigDecimal("0.01"));
        }
    }

    @Test
    void shouldPreserveSpreadAfterTick() {
        Instrument instrument = buildInstrument("AAPL", new BigDecimal("175.00"));
        BigDecimal originalSpread = instrument.getAskPrice().subtract(instrument.getBidPrice());

        priceTickService.tickInstrument(instrument, false);

        BigDecimal newSpread = instrument.getAskPrice().subtract(instrument.getBidPrice());
        assertThat(newSpread).isEqualByComparingTo(originalSpread);
    }

    @Test
    void shouldResetDayHighLowAndVolumeOnFirstTickOfDay() {
        Instrument instrument = buildInstrument("AAPL", new BigDecimal("175.00"));
        instrument.setDayHigh(new BigDecimal("180.00"));
        instrument.setDayLow(new BigDecimal("170.00"));
        instrument.setVolume(50000L);

        priceTickService.tickInstrument(instrument, true);

        assertThat(instrument.getDayHigh()).isEqualByComparingTo(instrument.getLastPrice());
        assertThat(instrument.getDayLow()).isEqualByComparingTo(instrument.getLastPrice());
        assertThat(instrument.getVolume()).isLessThanOrEqualTo(5000L);
    }

    @Test
    void shouldSetPreviousCloseOnFirstTickOfDay() {
        Instrument instrument = buildInstrument("AAPL", new BigDecimal("175.00"));

        priceTickService.tickInstrument(instrument, true);

        assertThat(instrument.getPreviousClose()).isEqualByComparingTo(new BigDecimal("175.00"));
    }

    @Test
    void shouldUpdateDayHighWhenNewPriceExceedsCurrent() {
        Instrument instrument = buildInstrument("AAPL", new BigDecimal("200.00"));
        instrument.setDayHigh(new BigDecimal("150.00"));
        instrument.setDayLow(new BigDecimal("100.00"));
        instrument.setVolume(1000L);

        priceTickService.tickInstrument(instrument, false);

        assertThat(instrument.getDayHigh()).isGreaterThanOrEqualTo(new BigDecimal("150.00"));
    }

    @Test
    void shouldIncrementVolumeBetween500And5000PerTick() {
        Instrument instrument = buildInstrument("AAPL", new BigDecimal("175.00"));
        instrument.setDayHigh(new BigDecimal("180.00"));
        instrument.setDayLow(new BigDecimal("170.00"));
        instrument.setVolume(1000L);

        priceTickService.tickInstrument(instrument, false);

        assertThat(instrument.getVolume()).isBetween(1500L, 6000L);
    }

    @Test
    void shouldInvalidateRedisCacheAfterTick() {
        Instrument instrument = buildInstrument("AAPL", new BigDecimal("175.00"));
        instrument.setDayHigh(new BigDecimal("180.00"));
        instrument.setDayLow(new BigDecimal("170.00"));
        instrument.setVolume(1000L);

        priceTickService.tickInstrument(instrument, false);

        verify(redisTemplate).delete("market:price:AAPL");
    }

    @Test
    void shouldSaveInstrumentAfterTick() {
        Instrument instrument = buildInstrument("AAPL", new BigDecimal("175.00"));
        instrument.setDayHigh(new BigDecimal("180.00"));
        instrument.setDayLow(new BigDecimal("170.00"));
        instrument.setVolume(1000L);

        priceTickService.tickInstrument(instrument, false);

        ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
        verify(instrumentRepository).save(captor.capture());
        assertThat(captor.getValue().getSymbol()).isEqualTo("AAPL");
    }

    @Test
    void shouldAddPriceToHistoryStoreAfterTick() {
        Instrument instrument = buildInstrument("AAPL", new BigDecimal("175.00"));
        instrument.setDayHigh(new BigDecimal("180.00"));
        instrument.setDayLow(new BigDecimal("170.00"));
        instrument.setVolume(1000L);

        priceTickService.tickInstrument(instrument, false);

        verify(priceHistoryStore).addPrice(eq("AAPL"), any(BigDecimal.class));
    }

    @Test
    void shouldBroadcastTickWithAllRequiredFields() throws Exception {
        Instrument instrument = buildInstrument("AAPL", new BigDecimal("175.00"));
        instrument.setDayHigh(new BigDecimal("180.00"));
        instrument.setDayLow(new BigDecimal("170.00"));
        instrument.setVolume(1000L);
        instrument.setPreviousClose(new BigDecimal("174.00"));
        List<String> history = List.of("174.00", "174.50", "175.00");

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(priceHistoryStore.getPriceHistory("AAPL")).thenReturn(history);

        priceTickService.tickInstrument(instrument, false);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(objectMapper).writeValueAsString(captor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) captor.getValue();
        assertThat(payload).containsKey("change");
        assertThat(payload).containsKey("changePct");
        assertThat(payload).containsKey("volume");
        assertThat(payload).containsKey("dayHigh");
        assertThat(payload).containsKey("dayLow");
        assertThat(payload).containsKey("previousClose");
        assertThat(payload).containsKey("priceHistory");
        assertThat(payload.get("priceHistory")).isEqualTo(history);
    }

    @Test
    void shouldApplyScenarioIncrementWhenScenarioIsActive() {
        Instrument instrument = buildInstrument("AAPL", new BigDecimal("175.00"));
        instrument.setDayHigh(new BigDecimal("180.00"));
        instrument.setDayLow(new BigDecimal("170.00"));
        instrument.setVolume(1000L);
        BigDecimal increment = new BigDecimal("0.8750");

        when(scenarioService.hasActiveScenario("AAPL")).thenReturn(true);
        when(scenarioService.applyScenarioTick("AAPL")).thenReturn(increment);

        priceTickService.tickInstrument(instrument, false);

        assertThat(instrument.getLastPrice()).isGreaterThan(new BigDecimal("175.00"));
    }

    @Test
    void shouldFallBackToRandomWalkWhenScenarioReturnsNull() {
        Instrument instrument = buildInstrument("AAPL", new BigDecimal("175.00"));
        instrument.setDayHigh(new BigDecimal("180.00"));
        instrument.setDayLow(new BigDecimal("170.00"));
        instrument.setVolume(1000L);

        when(scenarioService.hasActiveScenario("AAPL")).thenReturn(true);
        when(scenarioService.applyScenarioTick("AAPL")).thenReturn(null);

        priceTickService.tickInstrument(instrument, false);

        assertThat(instrument.getLastPrice()).isGreaterThanOrEqualTo(new BigDecimal("0.01"));
    }

    @Test
    void shouldSkipTickWhenMarketIsClosed() {
        when(marketDataService.isMarketOpen(any())).thenReturn(false);

        priceTickService.tick();

        verifyNoInteractions(instrumentRepository);
        verifyNoInteractions(redisTemplate);
    }

    // --- PriceHistoryStore unit tests ---

    @Test
    void shouldStoreUpToTwentyPricesInHistory() {
        PriceHistoryStore store = new PriceHistoryStore();
        for (int i = 1; i <= 25; i++) {
            store.addPrice("AAPL", new BigDecimal(String.valueOf(i)));
        }
        List<String> history = store.getPriceHistory("AAPL");
        assertThat(history).hasSize(PriceHistoryStore.MAX_HISTORY);
        // oldest entries (1-5) should be evicted, last 20 should be 6-25
        assertThat(history.get(0)).isEqualTo("6");
        assertThat(history.get(history.size() - 1)).isEqualTo("25");
    }

    @Test
    void shouldReturnEmptyListForSymbolWithNoHistory() {
        PriceHistoryStore store = new PriceHistoryStore();
        assertThat(store.getPriceHistory("UNKNOWN")).isEmpty();
    }

    @Test
    void shouldReturnPricesInChronologicalOrder() {
        PriceHistoryStore store = new PriceHistoryStore();
        store.addPrice("AAPL", new BigDecimal("174.00"));
        store.addPrice("AAPL", new BigDecimal("174.50"));
        store.addPrice("AAPL", new BigDecimal("175.00"));

        List<String> history = store.getPriceHistory("AAPL");

        assertThat(history).containsExactly("174.00", "174.50", "175.00");
    }

    @Test
    void shouldResetHistoryForSymbol() {
        PriceHistoryStore store = new PriceHistoryStore();
        store.addPrice("AAPL", new BigDecimal("175.00"));
        store.reset("AAPL");
        assertThat(store.getPriceHistory("AAPL")).isEmpty();
    }

    // --- helpers ---

    private Instrument buildInstrument(String symbol, BigDecimal price) {
        BigDecimal spread = new BigDecimal("0.10");
        return Instrument.builder()
                .symbol(symbol)
                .lastPrice(price)
                .bidPrice(price.subtract(spread.divide(new BigDecimal("2"))))
                .askPrice(price.add(spread.divide(new BigDecimal("2"))))
                .tradable(true)
                .tradingHalted(false)
                .minQuantity(1)
                .maxQuantity(10000)
                .dataTimestamp(Instant.now())
                .build();
    }
}
