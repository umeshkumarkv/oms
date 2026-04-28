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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
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

    private static final String CACHE_KEY_PREFIX = "market:price:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final InstrumentRepository instrumentRepository;
    private final InstrumentMapper instrumentMapper;
    private final MarketHolidayRepository marketHolidayRepository;
    private final MarketHolidayMapper marketHolidayMapper;
    private final InstrumentSummaryMapper instrumentSummaryMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public InstrumentResponse getInstrument(String symbol) {
        log.debug("Fetching instrument: symbol={}", symbol);
        String cacheKey = CACHE_KEY_PREFIX + symbol;

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof InstrumentResponse hit) {
            log.debug("Cache hit: symbol={}", symbol);
            return hit;
        }

        InstrumentResponse response = instrumentRepository.findById(symbol)
                .map(instrumentMapper::toResponse)
                .orElseThrow(() -> new SymbolNotFoundException("SYMBOL_NOT_FOUND",
                        "Symbol not found: " + symbol));

        redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL);
        log.debug("Cache populated: symbol={}", symbol);
        return response;
    }

    public MarketHoursResponse getMarketHours() {
        return getMarketHoursAt(ZonedDateTime.now(ZoneId.of(timezone)));
    }

    MarketHoursResponse getMarketHoursAt(ZonedDateTime now) {
        log.debug("Checking market hours: now={}", now);
        boolean open = isMarketOpen(now);
        String nextOpenDate = null;
        if (!open) {
            // If before market open, today may still open — search from today.
            // If at/after close (or weekend/holiday), today is done — search from tomorrow.
            LocalDate startCandidate = now.toLocalTime().isBefore(LocalTime.parse(openTime))
                    ? now.toLocalDate()
                    : now.toLocalDate().plusDays(1);
            nextOpenDate = computeNextOpenDate(startCandidate);
        }
        return MarketHoursResponse.builder()
                .marketOpen(open)
                .openTime(openTime)
                .closeTime(closeTime)
                .timezone(timezone)
                .nextOpenDate(nextOpenDate)
                .build();
    }

    private String computeNextOpenDate(LocalDate candidate) {
        while (true) {
            DayOfWeek day = candidate.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
                    && !marketHolidayRepository.existsById(candidate)) {
                return candidate.toString();
            }
            candidate = candidate.plusDays(1);
        }
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

    public boolean isMarketOpen(ZonedDateTime now) {
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        if (marketHolidayRepository.existsById(now.toLocalDate())) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        LocalTime open  = LocalTime.parse(openTime);
        LocalTime close = LocalTime.parse(closeTime);
        return !time.isBefore(open) && time.isBefore(close);
    }
}
