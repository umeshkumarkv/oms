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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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

    @InjectMocks
    private MarketDataService marketDataService;

    // --- getInstrument ---

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
    void shouldThrowSymbolNotFoundExceptionWhenSymbolIsUnknown() {
        when(instrumentRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marketDataService.getInstrument("UNKNOWN"))
                .isInstanceOf(SymbolNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
    }

    // --- isMarketOpen ---

    @Test
    void shouldReturnMarketOpenDuringTradingHours() {
        // Tuesday 10:00 ET
        ZonedDateTime tuesday10am = ZonedDateTime.of(2026, 4, 28, 10, 0, 0, 0, MarketDataService.NEW_YORK);
        assertThat(marketDataService.isMarketOpen(tuesday10am)).isTrue();
    }

    @Test
    void shouldReturnMarketOpenAtExactOpenTime() {
        // Monday 09:30 ET
        ZonedDateTime monday930 = ZonedDateTime.of(2026, 4, 27, 9, 30, 0, 0, MarketDataService.NEW_YORK);
        assertThat(marketDataService.isMarketOpen(monday930)).isTrue();
    }

    @Test
    void shouldReturnMarketClosedBeforeOpenTime() {
        // Wednesday 09:29 ET
        ZonedDateTime wednesday929 = ZonedDateTime.of(2026, 4, 29, 9, 29, 0, 0, MarketDataService.NEW_YORK);
        assertThat(marketDataService.isMarketOpen(wednesday929)).isFalse();
    }

    @Test
    void shouldReturnMarketClosedAtCloseTime() {
        // Thursday 16:00 ET — close is exclusive
        ZonedDateTime thursday16 = ZonedDateTime.of(2026, 4, 30, 16, 0, 0, 0, MarketDataService.NEW_YORK);
        assertThat(marketDataService.isMarketOpen(thursday16)).isFalse();
    }

    @Test
    void shouldReturnMarketClosedOnSaturday() {
        ZonedDateTime saturday = ZonedDateTime.of(2026, 4, 25, 12, 0, 0, 0, MarketDataService.NEW_YORK);
        assertThat(marketDataService.isMarketOpen(saturday)).isFalse();
    }

    @Test
    void shouldReturnMarketClosedOnSunday() {
        ZonedDateTime sunday = ZonedDateTime.of(2026, 4, 26, 12, 0, 0, 0, MarketDataService.NEW_YORK);
        assertThat(marketDataService.isMarketOpen(sunday)).isFalse();
    }

    // --- getMarketHours ---

    @Test
    void shouldReturnMarketHoursResponseWithCorrectFields() {
        MarketHoursResponse result = marketDataService.getMarketHours();

        assertThat(result).isNotNull();
        assertThat(result.getOpenTime()).isEqualTo("09:30:00");
        assertThat(result.getCloseTime()).isEqualTo("16:00:00");
        assertThat(result.getTimezone()).isEqualTo("America/New_York");
        assertThat(result.getMarketOpen()).isNotNull();
        assertThat(result.getNextOpenDate()).isNull();
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
