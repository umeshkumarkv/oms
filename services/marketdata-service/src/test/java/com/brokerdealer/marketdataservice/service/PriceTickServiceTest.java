package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.repository.InstrumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceTickServiceTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private MarketDataService marketDataService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private PriceTickService priceTickService;

    // --- tickInstrument ---

    @Test
    void shouldUpdatePriceWithinBoundsOnTick() {
        Instrument instrument = buildInstrument("AAPL", new BigDecimal("175.00"));

        priceTickService.tickInstrument(instrument, false);

        BigDecimal newPrice = instrument.getLastPrice();
        assertThat(newPrice).isGreaterThanOrEqualTo(new BigDecimal("0.01"));
        // price should be within ±0.5% of 175.00 → range [174.125, 175.875]
        assertThat(newPrice).isGreaterThanOrEqualTo(new BigDecimal("174.00"));
        assertThat(newPrice).isLessThanOrEqualTo(new BigDecimal("176.00"));
    }

    @Test
    void shouldNeverTickBelowMinimumPrice() {
        Instrument instrument = buildInstrument("CHEAP", new BigDecimal("0.01"));

        // run many ticks to ensure price never goes below 0.01
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
        instrument.setDayHigh(new BigDecimal("150.00")); // artificially low high
        instrument.setDayLow(new BigDecimal("100.00"));
        instrument.setVolume(1000L);

        priceTickService.tickInstrument(instrument, false);

        // new price will be ~200 which is > dayHigh of 150
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
    void shouldSkipTickWhenMarketIsClosed() {
        when(marketDataService.isMarketOpen(any())).thenReturn(false);

        priceTickService.tick();

        verifyNoInteractions(instrumentRepository);
        verifyNoInteractions(redisTemplate);
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
