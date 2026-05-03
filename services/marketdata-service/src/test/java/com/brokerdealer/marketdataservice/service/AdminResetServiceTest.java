package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.dto.response.ResetResponse;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminResetServiceTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private ScenarioService scenarioService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private AdminResetService adminResetService;

    @Test
    void shouldReturnResetCompleteStatus() {
        when(instrumentRepository.findAll()).thenReturn(List.of());

        ResetResponse response = adminResetService.reset();

        assertThat(response.getStatus()).isEqualTo("RESET_COMPLETE");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void shouldRestoreSeedPricesForAllInstruments() {
        Instrument aapl = buildInstrument("AAPL",
                new BigDecimal("200.00"), new BigDecimal("199.90"), new BigDecimal("200.10"),
                new BigDecimal("175.00"), new BigDecimal("174.95"), new BigDecimal("175.05"));

        when(instrumentRepository.findAll()).thenReturn(List.of(aapl));

        adminResetService.reset();

        assertThat(aapl.getLastPrice()).isEqualByComparingTo(new BigDecimal("175.00"));
        assertThat(aapl.getBidPrice()).isEqualByComparingTo(new BigDecimal("174.95"));
        assertThat(aapl.getAskPrice()).isEqualByComparingTo(new BigDecimal("175.05"));
    }

    @Test
    void shouldClearDayHighLowAndVolume() {
        Instrument aapl = buildInstrument("AAPL",
                new BigDecimal("200.00"), new BigDecimal("199.90"), new BigDecimal("200.10"),
                new BigDecimal("175.00"), new BigDecimal("174.95"), new BigDecimal("175.05"));
        aapl.setDayHigh(new BigDecimal("210.00"));
        aapl.setDayLow(new BigDecimal("190.00"));
        aapl.setVolume(100000L);

        when(instrumentRepository.findAll()).thenReturn(List.of(aapl));

        adminResetService.reset();

        assertThat(aapl.getDayHigh()).isNull();
        assertThat(aapl.getDayLow()).isNull();
        assertThat(aapl.getPreviousClose()).isNull();
        assertThat(aapl.getVolume()).isEqualTo(0L);
    }

    @Test
    void shouldInvalidateRedisCacheForAllInstruments() {
        Instrument aapl = buildInstrument("AAPL",
                new BigDecimal("200.00"), new BigDecimal("199.90"), new BigDecimal("200.10"),
                new BigDecimal("175.00"), new BigDecimal("174.95"), new BigDecimal("175.05"));
        Instrument msft = buildInstrument("MSFT",
                new BigDecimal("430.00"), new BigDecimal("429.90"), new BigDecimal("430.10"),
                new BigDecimal("415.00"), new BigDecimal("414.90"), new BigDecimal("415.10"));

        when(instrumentRepository.findAll()).thenReturn(List.of(aapl, msft));

        adminResetService.reset();

        verify(redisTemplate).delete("market:price:AAPL");
        verify(redisTemplate).delete("market:price:MSFT");
    }

    @Test
    void shouldClearAllActiveScenarios() {
        when(instrumentRepository.findAll()).thenReturn(List.of());

        adminResetService.reset();

        verify(scenarioService).clearAllScenarios();
    }

    @Test
    void shouldSaveAllInstrumentsAfterReset() {
        Instrument aapl = buildInstrument("AAPL",
                new BigDecimal("200.00"), new BigDecimal("199.90"), new BigDecimal("200.10"),
                new BigDecimal("175.00"), new BigDecimal("174.95"), new BigDecimal("175.05"));

        when(instrumentRepository.findAll()).thenReturn(List.of(aapl));

        adminResetService.reset();

        ArgumentCaptor<List<Instrument>> captor = ArgumentCaptor.forClass(List.class);
        verify(instrumentRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getSymbol()).isEqualTo("AAPL");
    }

    @Test
    void shouldSkipPriceResetWhenSeedPriceIsNull() {
        Instrument noSeed = Instrument.builder()
                .symbol("NOSEED")
                .lastPrice(new BigDecimal("100.00"))
                .bidPrice(new BigDecimal("99.90"))
                .askPrice(new BigDecimal("100.10"))
                .tradable(true)
                .tradingHalted(false)
                .minQuantity(1).maxQuantity(10000)
                .dataTimestamp(Instant.now())
                .build(); // seedLastPrice is null

        when(instrumentRepository.findAll()).thenReturn(List.of(noSeed));

        adminResetService.reset();

        // price should remain unchanged since seed is null
        assertThat(noSeed.getLastPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    // --- helpers ---

    private Instrument buildInstrument(String symbol,
                                       BigDecimal currentLast, BigDecimal currentBid, BigDecimal currentAsk,
                                       BigDecimal seedLast, BigDecimal seedBid, BigDecimal seedAsk) {
        return Instrument.builder()
                .symbol(symbol)
                .lastPrice(currentLast)
                .bidPrice(currentBid)
                .askPrice(currentAsk)
                .tradable(true)
                .tradingHalted(false)
                .minQuantity(1).maxQuantity(10000)
                .dataTimestamp(Instant.now())
                .seedLastPrice(seedLast)
                .seedBidPrice(seedBid)
                .seedAskPrice(seedAsk)
                .build();
    }
}
