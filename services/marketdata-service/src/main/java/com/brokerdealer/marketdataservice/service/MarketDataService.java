package com.brokerdealer.marketdataservice.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataService {

    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    static final LocalTime MARKET_OPEN  = LocalTime.of(9, 30);
    static final LocalTime MARKET_CLOSE = LocalTime.of(16, 0);

    private final InstrumentRepository instrumentRepository;
    private final InstrumentMapper instrumentMapper;
    private final MarketHolidayRepository marketHolidayRepository;
    private final MarketHolidayMapper marketHolidayMapper;
    private final InstrumentSummaryMapper instrumentSummaryMapper;

    public InstrumentResponse getInstrument(String symbol) {
        log.debug("Fetching instrument: symbol={}", symbol);
        return instrumentRepository.findById(symbol)
                .map(instrumentMapper::toResponse)
                .orElseThrow(() -> new SymbolNotFoundException("SYMBOL_NOT_FOUND",
                        "Symbol not found: " + symbol));
    }

    public MarketHoursResponse getMarketHours() {
        ZonedDateTime now = ZonedDateTime.now(NEW_YORK);
        log.debug("Checking market hours: now={}", now);
        boolean open = isMarketOpen(now);
        return MarketHoursResponse.builder()
                .marketOpen(open)
                .openTime("09:30:00")
                .closeTime("16:00:00")
                .timezone("America/New_York")
                .nextOpenDate(null)
                .build();
    }

    public List<MarketHolidayResponse> getHolidays() {
        log.debug("Fetching market holidays");
        return marketHolidayRepository.findAllByOrderByHolidayDateAsc()
                .stream()
                .map(marketHolidayMapper::toResponse)
                .toList();
    }

    public List<InstrumentSummaryResponse> getInstruments() {
        log.debug("Fetching all instruments summary");
        return instrumentRepository.findAll()
                .stream()
                .map(instrumentSummaryMapper::toSummary)
                .toList();
    }

    boolean isMarketOpen(ZonedDateTime now) {
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(MARKET_OPEN) && time.isBefore(MARKET_CLOSE);
    }
}
