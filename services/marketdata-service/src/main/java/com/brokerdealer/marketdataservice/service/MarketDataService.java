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
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${market.hours.open-time}")
    private String openTime;

    @Value("${market.hours.close-time}")
    private String closeTime;

    @Value("${market.hours.timezone}")
    private String timezone;

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
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timezone));
        log.debug("Checking market hours: now={}", now);
        boolean open = isMarketOpen(now);
        return MarketHoursResponse.builder()
                .marketOpen(open)
                .openTime(openTime)
                .closeTime(closeTime)
                .timezone(timezone)
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
        LocalTime open  = LocalTime.parse(openTime);
        LocalTime close = LocalTime.parse(closeTime);
        return !time.isBefore(open) && time.isBefore(close);
    }
}
