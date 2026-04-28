package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.domain.MarketHoliday;
import com.brokerdealer.marketdataservice.dto.response.InstrumentResponse;
import com.brokerdealer.marketdataservice.dto.response.InstrumentSummaryResponse;
import com.brokerdealer.marketdataservice.dto.response.MarketHolidayResponse;
import com.brokerdealer.marketdataservice.dto.response.MarketHoursResponse;
import com.brokerdealer.marketdataservice.exception.SymbolNotFoundException;
import com.brokerdealer.marketdataservice.mapper.InstrumentMapper;
import com.brokerdealer.marketdataservice.mapper.InstrumentSummaryMapper;
import com.brokerdealer.marketdataservice.mapper.MarketHolidayMapper;
import com.brokerdealer.marketdataservice.repository.InstrumentRepository;
import com.brokerdealer.marketdataservice.repository.MarketHolidayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private InstrumentMapper instrumentMapper;

    @Mock
    private MarketHolidayRepository marketHolidayRepository;

    @Mock
    private MarketHolidayMapper marketHolidayMapper;

    @Mock
    private InstrumentSummaryMapper instrumentSummaryMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private MarketDataService marketDataService;

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(marketDataService, "openTime", "09:30:00");
        ReflectionTestUtils.setField(marketDataService, "closeTime", "16:00:00");
        ReflectionTestUtils.setField(marketDataService, "timezone", "America/New_York");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null); // cache miss by default
    }

    // --- getInstrument: cache miss ---

    @Test
    void shouldReturnInstrumentResponseWhenSymbolIsValid() {
        Instrument instrument = buildInstrument("AAPL", false);
        InstrumentResponse expected = buildResponse("AAPL", false);

        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));
        when(instrumentMapper.toResponse(instrument)).thenReturn(expected);

        InstrumentResponse result = marketDataService.getInstrument("AAPL");

        assertThat(result).isNotNull();
        assertThat(result.getSymbol()).isEqualTo("AAPL");
        assertThat(result.getHalted()).isFalse();
        assertThat(result.getTradable()).isTrue();
        verify(valueOperations).set(eq("market:price:AAPL"), eq(expected), any(Duration.class));
    }

    @Test
    void shouldReturnHaltedTrueWhenSymbolIsHalted() {
        Instrument instrument = buildInstrument("HALT", true);
        InstrumentResponse expected = buildResponse("HALT", true);

        when(instrumentRepository.findById("HALT")).thenReturn(Optional.of(instrument));
        when(instrumentMapper.toResponse(instrument)).thenReturn(expected);

        InstrumentResponse result = marketDataService.getInstrument("HALT");

        assertThat(result.getSymbol()).isEqualTo("HALT");
        assertThat(result.getHalted()).isTrue();
        assertThat(result.getTradable()).isTrue();
    }

    @Test
    void shouldNormaliseSymbolToUppercaseBeforeLookup() {
        Instrument instrument = buildInstrument("AAPL", false);
        InstrumentResponse expected = buildResponse("AAPL", false);

        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));
        when(instrumentMapper.toResponse(instrument)).thenReturn(expected);

        InstrumentResponse result = marketDataService.getInstrument("aapl");

        assertThat(result.getSymbol()).isEqualTo("AAPL");
        verify(valueOperations).set(eq("market:price:AAPL"), eq(expected), any(Duration.class));
    }

    @Test
    void shouldThrowSymbolNotFoundExceptionWhenSymbolIsUnknown() {
        when(instrumentRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marketDataService.getInstrument("UNKNOWN"))
                .isInstanceOf(SymbolNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
    }

    // --- getInstrument: cache hit ---

    @Test
    void shouldReturnInstrumentFromCacheWhenCacheHit() {
        InstrumentResponse cached = buildResponse("AAPL", false);
        when(valueOperations.get("market:price:AAPL")).thenReturn(cached);

        InstrumentResponse result = marketDataService.getInstrument("AAPL");

        assertThat(result.getSymbol()).isEqualTo("AAPL");
        verifyNoInteractions(instrumentRepository);
    }

    // --- isMarketOpen ---

    @Test
    void shouldReturnMarketOpenDuringTradingHours() {
        // Tuesday 10:00 ET — not a holiday
        ZonedDateTime tuesday10am = ZonedDateTime.of(2026, 4, 28, 10, 0, 0, 0, NEW_YORK);
        when(marketHolidayRepository.existsById(LocalDate.of(2026, 4, 28))).thenReturn(false);
        assertThat(marketDataService.isMarketOpen(tuesday10am)).isTrue();
    }

    @Test
    void shouldReturnMarketOpenAtExactOpenTime() {
        // Monday 09:30 ET
        ZonedDateTime monday930 = ZonedDateTime.of(2026, 4, 27, 9, 30, 0, 0, NEW_YORK);
        when(marketHolidayRepository.existsById(LocalDate.of(2026, 4, 27))).thenReturn(false);
        assertThat(marketDataService.isMarketOpen(monday930)).isTrue();
    }

    @Test
    void shouldReturnMarketClosedBeforeOpenTime() {
        // Wednesday 09:29 ET
        ZonedDateTime wednesday929 = ZonedDateTime.of(2026, 4, 29, 9, 29, 0, 0, NEW_YORK);
        assertThat(marketDataService.isMarketOpen(wednesday929)).isFalse();
    }

    @Test
    void shouldReturnMarketClosedAtCloseTime() {
        // Thursday 16:00 ET — close is exclusive
        ZonedDateTime thursday16 = ZonedDateTime.of(2026, 4, 30, 16, 0, 0, 0, NEW_YORK);
        assertThat(marketDataService.isMarketOpen(thursday16)).isFalse();
    }

    @Test
    void shouldReturnMarketClosedOnSaturday() {
        ZonedDateTime saturday = ZonedDateTime.of(2026, 4, 25, 12, 0, 0, 0, NEW_YORK);
        assertThat(marketDataService.isMarketOpen(saturday)).isFalse();
    }

    @Test
    void shouldReturnMarketClosedOnSunday() {
        ZonedDateTime sunday = ZonedDateTime.of(2026, 4, 26, 12, 0, 0, 0, NEW_YORK);
        assertThat(marketDataService.isMarketOpen(sunday)).isFalse();
    }

    @Test
    void shouldReturnMarketClosedOnHoliday() {
        // Good Friday 2026-04-03 during trading hours
        ZonedDateTime goodFriday = ZonedDateTime.of(2026, 4, 3, 11, 0, 0, 0, NEW_YORK);
        when(marketHolidayRepository.existsById(LocalDate.of(2026, 4, 3))).thenReturn(true);
        assertThat(marketDataService.isMarketOpen(goodFriday)).isFalse();
    }

    // --- getMarketHoursAt ---

    @Test
    void shouldReturnMarketHoursResponseWithCorrectFieldsWhenOpen() {
        ZonedDateTime tuesday10am = ZonedDateTime.of(2026, 4, 28, 10, 0, 0, 0, NEW_YORK);
        when(marketHolidayRepository.existsById(LocalDate.of(2026, 4, 28))).thenReturn(false);

        MarketHoursResponse result = marketDataService.getMarketHoursAt(tuesday10am);

        assertThat(result.getOpenTime()).isEqualTo("09:30:00");
        assertThat(result.getCloseTime()).isEqualTo("16:00:00");
        assertThat(result.getTimezone()).isEqualTo("America/New_York");
        assertThat(result.getMarketOpen()).isTrue();
        assertThat(result.getNextOpenDate()).isNull();
    }

    @Test
    void shouldReturnNextOpenDateSkippingWeekendsWhenMarketClosed() {
        // Friday 18:00 ET — market closed, next open is Monday 2026-04-27
        ZonedDateTime fridayEvening = ZonedDateTime.of(2026, 4, 24, 18, 0, 0, 0, NEW_YORK);
        // Saturday 2026-04-25 and Sunday 2026-04-26 are weekends, Monday 2026-04-27 is open
        when(marketHolidayRepository.existsById(any(LocalDate.class))).thenReturn(false);

        MarketHoursResponse result = marketDataService.getMarketHoursAt(fridayEvening);

        assertThat(result.getMarketOpen()).isFalse();
        assertThat(result.getNextOpenDate()).isEqualTo("2026-04-27");
    }

    @Test
    void shouldReturnTodayAsNextOpenDateWhenBeforeMarketOpen() {
        // Tuesday 00:45 ET — market hasn't opened yet today, so nextOpenDate should be today
        ZonedDateTime tuesdayEarlyMorning = ZonedDateTime.of(2026, 4, 28, 0, 45, 0, 0, NEW_YORK);
        when(marketHolidayRepository.existsById(LocalDate.of(2026, 4, 28))).thenReturn(false);

        MarketHoursResponse result = marketDataService.getMarketHoursAt(tuesdayEarlyMorning);

        assertThat(result.getMarketOpen()).isFalse();
        assertThat(result.getNextOpenDate()).isEqualTo("2026-04-28");
    }

    @Test
    void shouldReturnNextOpenDateSkippingHolidayWhenMarketClosed() {
        // Thursday evening before Good Friday 2026-04-03 (holiday) + weekend → next open Mon 2026-04-06
        ZonedDateTime thursdayEvening = ZonedDateTime.of(2026, 4, 2, 18, 0, 0, 0, NEW_YORK);
        // Only 2026-04-03 is a holiday; all other dates are normal trading days
        when(marketHolidayRepository.existsById(any(LocalDate.class))).thenAnswer(inv -> {
            LocalDate date = inv.getArgument(0);
            return LocalDate.of(2026, 4, 3).equals(date);
        });

        MarketHoursResponse result = marketDataService.getMarketHoursAt(thursdayEvening);

        assertThat(result.getMarketOpen()).isFalse();
        assertThat(result.getNextOpenDate()).isEqualTo("2026-04-06");
    }

    // --- getHolidays ---

    @Test
    void shouldReturnHolidayListOrderedByDate() {
        MarketHoliday h1 = MarketHoliday.builder()
                .holidayDate(LocalDate.of(2026, 1, 1))
                .holidayName("New Year's Day")
                .build();
        MarketHolidayResponse r1 = MarketHolidayResponse.builder()
                .holidayDate("2026-01-01")
                .holidayName("New Year's Day")
                .build();

        when(marketHolidayRepository.findAllByOrderByHolidayDateAsc()).thenReturn(List.of(h1));
        when(marketHolidayMapper.toResponse(h1)).thenReturn(r1);

        List<MarketHolidayResponse> result = marketDataService.getHolidays();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHolidayDate()).isEqualTo("2026-01-01");
        assertThat(result.get(0).getHolidayName()).isEqualTo("New Year's Day");
    }

    // --- getInstruments ---

    @Test
    void shouldReturnInstrumentSummaryList() {
        Instrument instrument = buildInstrument("AAPL", false);
        InstrumentSummaryResponse summary = InstrumentSummaryResponse.builder()
                .symbol("AAPL").tradable(true).halted(false).build();

        when(instrumentRepository.findAll()).thenReturn(List.of(instrument));
        when(instrumentSummaryMapper.toSummary(instrument)).thenReturn(summary);

        List<InstrumentSummaryResponse> result = marketDataService.getInstruments();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
        assertThat(result.get(0).getTradable()).isTrue();
        assertThat(result.get(0).getHalted()).isFalse();
    }

    // --- helpers ---

    private Instrument buildInstrument(String symbol, boolean halted) {
        return Instrument.builder()
                .symbol(symbol)
                .bidPrice(new BigDecimal("174.95"))
                .askPrice(new BigDecimal("175.05"))
                .lastPrice(new BigDecimal("175.00"))
                .tradable(true)
                .tradingHalted(halted)
                .minQuantity(1)
                .maxQuantity(10000)
                .dataTimestamp(Instant.now())
                .build();
    }

    private InstrumentResponse buildResponse(String symbol, boolean halted) {
        return InstrumentResponse.builder()
                .symbol(symbol)
                .bidPrice("174.95")
                .askPrice("175.05")
                .lastPrice("175.00")
                .tradable(true)
                .halted(halted)
                .minQuantity(1)
                .maxQuantity(10000)
                .dataTimestamp(Instant.now().toString())
                .build();
    }
}
