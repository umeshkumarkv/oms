package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.domain.MarketHoliday;
import com.brokerdealer.marketdataservice.dto.response.InstrumentResponse;
import com.brokerdealer.marketdataservice.dto.response.InstrumentSummaryResponse;
import com.brokerdealer.marketdataservice.dto.response.MarketHolidayResponse;
import com.brokerdealer.marketdataservice.dto.response.MarketHoursResponse;
import com.brokerdealer.marketdataservice.exception.SymbolNotFoundException;
import com.brokerdealer.marketdataservice.dto.response.InstrumentSearchResponse;
import com.brokerdealer.marketdataservice.mapper.InstrumentMapper;
import com.brokerdealer.marketdataservice.mapper.InstrumentSearchMapper;
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
    private InstrumentSummaryMapper instrumentSummaryMapper;

    @Mock
    private InstrumentSearchMapper instrumentSearchMapper;

    @Mock
    private MarketHolidayRepository marketHolidayRepository;

    @Mock
    private MarketHolidayMapper marketHolidayMapper;

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
        lenient().when(marketHolidayRepository.existsById(any(LocalDate.class))).thenReturn(false);
    }

    // --- getInstrument: cache miss ---

    @Test
    void shouldReturnInstrumentResponseWhenSymbolIsValid() {
        Instrument instrument = buildInstrument("AAPL", false);
        InstrumentResponse base = buildBaseResponse("AAPL", false);

        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));
        when(instrumentMapper.toResponse(instrument)).thenReturn(base);

        InstrumentResponse result = marketDataService.getInstrument("AAPL");

        assertThat(result).isNotNull();
        assertThat(result.getSymbol()).isEqualTo("AAPL");
        assertThat(result.getHalted()).isFalse();
        assertThat(result.getTradable()).isTrue();
        verify(valueOperations).set(eq("market:price:AAPL"), any(InstrumentResponse.class), any(Duration.class));
    }

    @Test
    void shouldReturnHaltedTrueWhenSymbolIsHalted() {
        Instrument instrument = buildInstrument("HALT", true);
        InstrumentResponse base = buildBaseResponse("HALT", true);

        when(instrumentRepository.findById("HALT")).thenReturn(Optional.of(instrument));
        when(instrumentMapper.toResponse(instrument)).thenReturn(base);

        InstrumentResponse result = marketDataService.getInstrument("HALT");

        assertThat(result.getSymbol()).isEqualTo("HALT");
        assertThat(result.getHalted()).isTrue();
        assertThat(result.getTradable()).isTrue();
    }

    @Test
    void shouldNormaliseSymbolToUppercaseBeforeLookup() {
        Instrument instrument = buildInstrument("AAPL", false);
        InstrumentResponse base = buildBaseResponse("AAPL", false);

        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));
        when(instrumentMapper.toResponse(instrument)).thenReturn(base);

        InstrumentResponse result = marketDataService.getInstrument("aapl");

        assertThat(result.getSymbol()).isEqualTo("AAPL");
        verify(valueOperations).set(eq("market:price:AAPL"), any(InstrumentResponse.class), any(Duration.class));
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
        InstrumentResponse cached = buildBaseResponse("AAPL", false);
        when(valueOperations.get("market:price:AAPL")).thenReturn(cached);

        InstrumentResponse result = marketDataService.getInstrument("AAPL");

        assertThat(result.getSymbol()).isEqualTo("AAPL");
        verifyNoInteractions(instrumentRepository);
    }

    // --- change / changePct computation ---

    @Test
    void shouldComputeChangeCorrectly() {
        BigDecimal change = new BigDecimal(marketDataService.computeChange(
                new BigDecimal("176.25"), new BigDecimal("175.00")));
        assertThat(change).isEqualByComparingTo(new BigDecimal("1.25"));
    }

    @Test
    void shouldComputeChangePctCorrectly() {
        // change = 1.25, previousClose = 175 → changePct = (1.25/175)*100 = 0.71%
        String pct = marketDataService.computeChangePct(
                new BigDecimal("176.25"), new BigDecimal("175.00"));
        assertThat(new BigDecimal(pct)).isEqualByComparingTo(new BigDecimal("0.71"));
    }

    @Test
    void shouldReturnZeroChangeWhenPreviousCloseIsNull() {
        assertThat(marketDataService.computeChange(new BigDecimal("175.00"), null)).isEqualTo("0");
    }

    @Test
    void shouldReturnZeroChangePctWhenPreviousCloseIsZero() {
        assertThat(marketDataService.computeChangePct(
                new BigDecimal("175.00"), BigDecimal.ZERO)).isEqualTo("0");
    }

    @Test
    void shouldReturnZeroChangeWhenLastPriceIsNull() {
        assertThat(marketDataService.computeChange(null, new BigDecimal("175.00"))).isEqualTo("0");
    }

    @Test
    void shouldEnrichResponseWithMarketStatusOpenOrClosed() {
        Instrument instrument = buildInstrument("AAPL", false);
        InstrumentResponse base = buildBaseResponse("AAPL", false);

        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));
        when(instrumentMapper.toResponse(instrument)).thenReturn(base);

        InstrumentResponse result = marketDataService.getInstrument("AAPL");

        assertThat(result.getMarketStatus()).isIn("OPEN", "CLOSED");
    }

    @Test
    void shouldEnrichResponseWithChangeAndChangePct() {
        Instrument instrument = buildInstrument("AAPL", false);
        instrument.setLastPrice(new BigDecimal("176.25"));
        instrument.setPreviousClose(new BigDecimal("175.00"));
        InstrumentResponse base = buildBaseResponse("AAPL", false);

        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));
        when(instrumentMapper.toResponse(instrument)).thenReturn(base);

        InstrumentResponse result = marketDataService.getInstrument("AAPL");

        assertThat(new BigDecimal(result.getChange())).isEqualByComparingTo(new BigDecimal("1.25"));
        assertThat(new BigDecimal(result.getChangePct())).isEqualByComparingTo(new BigDecimal("0.71"));
    }

    // --- isMarketOpen ---

    @Test
    void shouldReturnMarketOpenDuringTradingHours() {
        ZonedDateTime tuesday10am = ZonedDateTime.of(2026, 4, 28, 10, 0, 0, 0, NEW_YORK);
        when(marketHolidayRepository.existsById(LocalDate.of(2026, 4, 28))).thenReturn(false);
        assertThat(marketDataService.isMarketOpen(tuesday10am)).isTrue();
    }

    @Test
    void shouldReturnMarketOpenAtExactOpenTime() {
        ZonedDateTime monday930 = ZonedDateTime.of(2026, 4, 27, 9, 30, 0, 0, NEW_YORK);
        when(marketHolidayRepository.existsById(LocalDate.of(2026, 4, 27))).thenReturn(false);
        assertThat(marketDataService.isMarketOpen(monday930)).isTrue();
    }

    @Test
    void shouldReturnMarketClosedBeforeOpenTime() {
        ZonedDateTime wednesday929 = ZonedDateTime.of(2026, 4, 29, 9, 29, 0, 0, NEW_YORK);
        assertThat(marketDataService.isMarketOpen(wednesday929)).isFalse();
    }

    @Test
    void shouldReturnMarketClosedAtCloseTime() {
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
        ZonedDateTime fridayEvening = ZonedDateTime.of(2026, 4, 24, 18, 0, 0, 0, NEW_YORK);
        when(marketHolidayRepository.existsById(any(LocalDate.class))).thenReturn(false);

        MarketHoursResponse result = marketDataService.getMarketHoursAt(fridayEvening);

        assertThat(result.getMarketOpen()).isFalse();
        assertThat(result.getNextOpenDate()).isEqualTo("2026-04-27");
    }

    @Test
    void shouldReturnTodayAsNextOpenDateWhenBeforeMarketOpen() {
        ZonedDateTime tuesdayEarlyMorning = ZonedDateTime.of(2026, 4, 28, 0, 45, 0, 0, NEW_YORK);
        when(marketHolidayRepository.existsById(LocalDate.of(2026, 4, 28))).thenReturn(false);

        MarketHoursResponse result = marketDataService.getMarketHoursAt(tuesdayEarlyMorning);

        assertThat(result.getMarketOpen()).isFalse();
        assertThat(result.getNextOpenDate()).isEqualTo("2026-04-28");
    }

    @Test
    void shouldReturnNextOpenDateSkippingHolidayWhenMarketClosed() {
        ZonedDateTime thursdayEvening = ZonedDateTime.of(2026, 4, 2, 18, 0, 0, 0, NEW_YORK);
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

    // --- DST transition ---

    @Test
    void shouldReturnMarketOpenOnFirstTradingDayAfterSpringForward() {
        ZonedDateTime monday930Edt = ZonedDateTime.of(2026, 3, 9, 9, 30, 0, 0, NEW_YORK);
        when(marketHolidayRepository.existsById(LocalDate.of(2026, 3, 9))).thenReturn(false);
        assertThat(marketDataService.isMarketOpen(monday930Edt)).isTrue();
    }

    @Test
    void shouldReturnMarketOpenOnFirstTradingDayAfterFallBack() {
        ZonedDateTime monday930Est = ZonedDateTime.of(2026, 11, 2, 9, 30, 0, 0, NEW_YORK);
        when(marketHolidayRepository.existsById(LocalDate.of(2026, 11, 2))).thenReturn(false);
        assertThat(marketDataService.isMarketOpen(monday930Est)).isTrue();
    }

    @Test
    void shouldReturnMarketClosedAtExactCloseOnDstTransitionDay() {
        ZonedDateTime closeTimeEdt = ZonedDateTime.of(2026, 3, 9, 16, 0, 0, 0, NEW_YORK);
        when(marketHolidayRepository.existsById(LocalDate.of(2026, 3, 9))).thenReturn(false);
        assertThat(marketDataService.isMarketOpen(closeTimeEdt)).isFalse();
    }

    // --- getInstruments ---

    @Test
    void shouldReturnInstrumentSummaryListWithAllNewFields() {
        Instrument instrument = buildInstrument("AAPL", false);
        instrument.setLastPrice(new BigDecimal("176.25"));
        instrument.setPreviousClose(new BigDecimal("175.00"));
        instrument.setDayHigh(new BigDecimal("177.00"));
        instrument.setDayLow(new BigDecimal("174.50"));
        instrument.setVolume(100000L);
        InstrumentSummaryResponse base = buildBaseSummaryResponse("AAPL", false);

        when(instrumentRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of(instrument));
        when(instrumentSummaryMapper.toSummary(instrument)).thenReturn(base);

        List<InstrumentSummaryResponse> result = marketDataService.getInstruments();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
        assertThat(result.get(0).getMarketStatus()).isIn("OPEN", "CLOSED");
        assertThat(new BigDecimal(result.get(0).getChange())).isEqualByComparingTo(new BigDecimal("1.25"));
        assertThat(new BigDecimal(result.get(0).getChangePct())).isEqualByComparingTo(new BigDecimal("0.71"));
    }

    @Test
    void shouldReturnInstrumentSummaryList() {
        Instrument instrument = buildInstrument("AAPL", false);
        InstrumentSummaryResponse base = buildBaseSummaryResponse("AAPL", false);

        when(instrumentRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of(instrument));
        when(instrumentSummaryMapper.toSummary(instrument)).thenReturn(base);

        List<InstrumentSummaryResponse> result = marketDataService.getInstruments();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
        assertThat(result.get(0).getTradable()).isTrue();
        assertThat(result.get(0).getHalted()).isFalse();
        assertThat(result.get(0).getLastPrice()).isEqualTo("175.00");
        assertThat(result.get(0).getBidPrice()).isEqualTo("174.95");
        assertThat(result.get(0).getAskPrice()).isEqualTo("175.05");
    }

    @Test
    void shouldReturnInstrumentsSortedAlphabeticallyBySymbol() {
        Instrument msft = buildInstrument("MSFT", false);
        Instrument aapl = buildInstrument("AAPL", false);
        InstrumentSummaryResponse msftSummary = buildBaseSummaryResponse("MSFT", false);
        InstrumentSummaryResponse aaplSummary = buildBaseSummaryResponse("AAPL", false);

        when(instrumentRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of(aapl, msft));
        when(instrumentSummaryMapper.toSummary(aapl)).thenReturn(aaplSummary);
        when(instrumentSummaryMapper.toSummary(msft)).thenReturn(msftSummary);

        List<InstrumentSummaryResponse> result = marketDataService.getInstruments();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
        assertThat(result.get(1).getSymbol()).isEqualTo("MSFT");
    }

    @Test
    void shouldReturnHaltedTrueForHaltInstrumentInSummaryList() {
        Instrument haltInstrument = buildInstrument("HALT", true);
        InstrumentSummaryResponse haltSummary = buildBaseSummaryResponse("HALT", true);

        when(instrumentRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of(haltInstrument));
        when(instrumentSummaryMapper.toSummary(haltInstrument)).thenReturn(haltSummary);

        List<InstrumentSummaryResponse> result = marketDataService.getInstruments();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("HALT");
        assertThat(result.get(0).getHalted()).isTrue();
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

    private InstrumentResponse buildBaseResponse(String symbol, boolean halted) {
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

    private InstrumentSummaryResponse buildBaseSummaryResponse(String symbol, boolean halted) {
        return InstrumentSummaryResponse.builder()
                .symbol(symbol)
                .bidPrice("174.95")
                .askPrice("175.05")
                .lastPrice("175.00")
                .tradable(true)
                .halted(halted)
                .build();
    }

    // --- searchInstruments ---

    @Test
    void shouldReturnMatchingInstrumentsBySymbolStartsWith() {
        Instrument aapl = buildSearchInstrument("AAPL", "Apple Inc.", "037833100", "AAPL-US", "NASDAQ");
        InstrumentSearchResponse aaplResp = buildSearchResponse("AAPL", "Apple Inc.", "037833100");

        when(instrumentRepository.findAllByTradableIsTrueAndTradingHaltedIsFalseOrderBySymbolAsc())
                .thenReturn(List.of(aapl));
        when(instrumentSearchMapper.toSearchResponse(aapl)).thenReturn(aaplResp);

        List<InstrumentSearchResponse> result = marketDataService.searchInstruments("AAP", 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
    }

    @Test
    void shouldReturnMatchingInstrumentsByCompanyName() {
        Instrument aapl = buildSearchInstrument("AAPL", "Apple Inc.", "037833100", "AAPL-US", "NASDAQ");
        InstrumentSearchResponse aaplResp = buildSearchResponse("AAPL", "Apple Inc.", "037833100");

        when(instrumentRepository.findAllByTradableIsTrueAndTradingHaltedIsFalseOrderBySymbolAsc())
                .thenReturn(List.of(aapl));
        when(instrumentSearchMapper.toSearchResponse(aapl)).thenReturn(aaplResp);

        List<InstrumentSearchResponse> result = marketDataService.searchInstruments("Apple", 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
    }

    @Test
    void shouldReturnMatchingInstrumentsByExactCusip() {
        Instrument aapl = buildSearchInstrument("AAPL", "Apple Inc.", "037833100", "AAPL-US", "NASDAQ");
        InstrumentSearchResponse aaplResp = buildSearchResponse("AAPL", "Apple Inc.", "037833100");

        when(instrumentRepository.findAllByTradableIsTrueAndTradingHaltedIsFalseOrderBySymbolAsc())
                .thenReturn(List.of(aapl));
        when(instrumentSearchMapper.toSearchResponse(aapl)).thenReturn(aaplResp);

        List<InstrumentSearchResponse> result = marketDataService.searchInstruments("037833100", 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCusip()).isEqualTo("037833100");
    }

    @Test
    void shouldReturnEmptyListWhenNoMatchFound() {
        Instrument aapl = buildSearchInstrument("AAPL", "Apple Inc.", "037833100", "AAPL-US", "NASDAQ");

        when(instrumentRepository.findAllByTradableIsTrueAndTradingHaltedIsFalseOrderBySymbolAsc())
                .thenReturn(List.of(aapl));

        List<InstrumentSearchResponse> result = marketDataService.searchInstruments("XYZNOTREAL", 10);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldLimitSearchResultsByParameter() {
        Instrument aapl = buildSearchInstrument("AAPL", "Apple Inc.", "037833100", "AAPL-US", "NASDAQ");
        Instrument amzn = buildSearchInstrument("AMZN", "Amazon.com Inc.", "023135106", "AMZN-US", "NASDAQ");
        Instrument meta = buildSearchInstrument("META", "Meta Platforms Inc.", "30303M102", "META-US", "NASDAQ");
        InstrumentSearchResponse aaplResp = buildSearchResponse("AAPL", "Apple Inc.", "037833100");
        InstrumentSearchResponse amznResp = buildSearchResponse("AMZN", "Amazon.com Inc.", "023135106");

        when(instrumentRepository.findAllByTradableIsTrueAndTradingHaltedIsFalseOrderBySymbolAsc())
                .thenReturn(List.of(aapl, amzn, meta));
        when(instrumentSearchMapper.toSearchResponse(aapl)).thenReturn(aaplResp);
        when(instrumentSearchMapper.toSearchResponse(amzn)).thenReturn(amznResp);

        List<InstrumentSearchResponse> result = marketDataService.searchInstruments("A", 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldCapLimitAtTwenty() {
        Instrument aapl = buildSearchInstrument("AAPL", "Apple Inc.", "037833100", "AAPL-US", "NASDAQ");
        InstrumentSearchResponse aaplResp = buildSearchResponse("AAPL", "Apple Inc.", "037833100");

        when(instrumentRepository.findAllByTradableIsTrueAndTradingHaltedIsFalseOrderBySymbolAsc())
                .thenReturn(List.of(aapl));
        when(instrumentSearchMapper.toSearchResponse(aapl)).thenReturn(aaplResp);

        List<InstrumentSearchResponse> result = marketDataService.searchInstruments("AAPL", 50);

        assertThat(result).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void shouldOrderExactSymbolMatchFirst() {
        Instrument aapl = buildSearchInstrument("AAPL", "Apple Inc.", "037833100", "AAPL-US", "NASDAQ");
        Instrument aaplx = buildSearchInstrument("AAPLX", "Apple Extended", "999999999", "AAPLX-US", "NASDAQ");
        InstrumentSearchResponse aaplResp = buildSearchResponse("AAPL", "Apple Inc.", "037833100");
        InstrumentSearchResponse aaplxResp = buildSearchResponse("AAPLX", "Apple Extended", "999999999");

        when(instrumentRepository.findAllByTradableIsTrueAndTradingHaltedIsFalseOrderBySymbolAsc())
                .thenReturn(List.of(aaplx, aapl));
        when(instrumentSearchMapper.toSearchResponse(aapl)).thenReturn(aaplResp);
        when(instrumentSearchMapper.toSearchResponse(aaplx)).thenReturn(aaplxResp);

        List<InstrumentSearchResponse> result = marketDataService.searchInstruments("AAPL", 10);

        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
    }

    @Test
    void shouldPopulateCacheOnCacheMiss() {
        Instrument aapl = buildSearchInstrument("AAPL", "Apple Inc.", "037833100", "AAPL-US", "NASDAQ");
        InstrumentSearchResponse aaplResp = buildSearchResponse("AAPL", "Apple Inc.", "037833100");

        when(instrumentRepository.findAllByTradableIsTrueAndTradingHaltedIsFalseOrderBySymbolAsc())
                .thenReturn(List.of(aapl));
        when(instrumentSearchMapper.toSearchResponse(aapl)).thenReturn(aaplResp);

        marketDataService.searchInstruments("AAPL", 10);

        verify(valueOperations).set(eq("market:search:aapl"), any(List.class), eq(Duration.ofSeconds(10)));
    }

    @Test
    void shouldReturnCachedResultsOnSearchCacheHit() {
        List<InstrumentSearchResponse> cached = List.of(buildSearchResponse("AAPL", "Apple Inc.", "037833100"));
        when(valueOperations.get("market:search:aapl")).thenReturn(cached);

        List<InstrumentSearchResponse> result = marketDataService.searchInstruments("AAPL", 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
        verifyNoInteractions(instrumentSearchMapper);
    }

    @Test
    void shouldFallThroughToDbWhenCacheContainsLinkedHashMap() {
        // Simulate Redis deserializing InstrumentSearchResponse → LinkedHashMap (type info missing)
        List<java.util.LinkedHashMap<String, Object>> staleCache = List.of(new java.util.LinkedHashMap<>());
        Instrument aapl = buildSearchInstrument("AAPL", "Apple Inc.", "037833100", "AAPL-US", "NASDAQ");
        InstrumentSearchResponse aaplResp = buildSearchResponse("AAPL", "Apple Inc.", "037833100");

        when(valueOperations.get("market:search:aapl")).thenReturn(staleCache);
        when(instrumentRepository.findAllByTradableIsTrueAndTradingHaltedIsFalseOrderBySymbolAsc())
                .thenReturn(List.of(aapl));
        when(instrumentSearchMapper.toSearchResponse(aapl)).thenReturn(aaplResp);

        List<InstrumentSearchResponse> result = marketDataService.searchInstruments("AAPL", 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
        verify(instrumentRepository).findAllByTradableIsTrueAndTradingHaltedIsFalseOrderBySymbolAsc();
    }

    private Instrument buildSearchInstrument(String symbol, String companyName,
            String cusip, String secNumber, String exchange) {
        return Instrument.builder()
                .symbol(symbol)
                .lastPrice(new BigDecimal("175.00"))
                .tradable(true)
                .tradingHalted(false)
                .companyName(companyName)
                .cusip(cusip)
                .secNumber(secNumber)
                .exchange(exchange)
                .build();
    }

    private InstrumentSearchResponse buildSearchResponse(String symbol, String companyName, String cusip) {
        return InstrumentSearchResponse.builder()
                .symbol(symbol)
                .companyName(companyName)
                .cusip(cusip)
                .lastPrice("175.00")
                .tradable(true)
                .build();
    }
}
