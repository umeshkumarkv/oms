package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.dto.response.InstrumentResponse;
import com.brokerdealer.marketdataservice.dto.response.InstrumentSearchResponse;
import com.brokerdealer.marketdataservice.dto.response.InstrumentSummaryResponse;
import com.brokerdealer.marketdataservice.dto.response.MarketConfigResponse;
import com.brokerdealer.marketdataservice.dto.response.MarketHolidayResponse;
import com.brokerdealer.marketdataservice.dto.response.MarketHoursResponse;
import com.brokerdealer.marketdataservice.exception.SymbolNotFoundException;
import com.brokerdealer.marketdataservice.mapper.InstrumentMapper;
import com.brokerdealer.marketdataservice.mapper.InstrumentSearchMapper;
import com.brokerdealer.marketdataservice.mapper.InstrumentSummaryMapper;
import com.brokerdealer.marketdataservice.mapper.MarketHolidayMapper;
import com.brokerdealer.marketdataservice.repository.InstrumentRepository;
import com.brokerdealer.marketdataservice.repository.MarketHolidayRepository;
import com.brokerdealer.marketdataservice.websocket.MarketDataWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
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
    private static final String SEARCH_CACHE_KEY_PREFIX = "market:search:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration SEARCH_CACHE_TTL = Duration.ofSeconds(10);
    private static final int SEARCH_MAX_LIMIT = 20;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Value("${market.simulation.bypass-market-hours:false}")
    private boolean bypassMarketHours;

    @Value("${market.polling.interval-ms:5000}")
    private long pollingIntervalMs;

    private final InstrumentRepository instrumentRepository;
    private final InstrumentMapper instrumentMapper;
    private final InstrumentSummaryMapper instrumentSummaryMapper;
    private final InstrumentSearchMapper instrumentSearchMapper;
    private final MarketHolidayRepository marketHolidayRepository;
    private final MarketHolidayMapper marketHolidayMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MarketDataWebSocketHandler webSocketHandler;

    public InstrumentResponse getInstrument(String symbol) {
        String normalised = symbol.toUpperCase();
        log.debug("Fetching instrument: symbol={}", normalised);
        String cacheKey = CACHE_KEY_PREFIX + normalised;

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof InstrumentResponse hit) {
            log.debug("Cache hit: symbol={}", normalised);
            return hit;
        }

        InstrumentResponse response = instrumentRepository.findById(normalised)
                .map(instrument -> {
                    InstrumentResponse base = instrumentMapper.toResponse(instrument);
                    return enrichInstrumentResponse(base,
                            instrument.getLastPrice(),
                            instrument.getPreviousClose());
                })
                .orElseThrow(() -> new SymbolNotFoundException("SYMBOL_NOT_FOUND",
                        "Symbol not found: " + normalised));

        redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL);
        log.debug("Cache populated: symbol={}", normalised);
        return response;
    }

    public MarketHoursResponse getMarketHours() {
        return getMarketHoursAt(ZonedDateTime.now(ZoneId.of(timezone)));
    }

    public MarketConfigResponse getMarketConfig() {
        MarketHoursResponse hours = getMarketHours();
        String dataSource = webSocketHandler.hasActiveSessions() ? "WEBSOCKET" : "POLLING";
        return MarketConfigResponse.builder()
                .pollingIntervalMs(pollingIntervalMs)
                .marketOpen(Boolean.TRUE.equals(hours.getMarketOpen()))
                .marketHours(MarketConfigResponse.MarketHoursConfig.builder()
                        .start(openTime)
                        .end(closeTime)
                        .timezone(timezone)
                        .build())
                .dataSource(dataSource)
                .build();
    }

    MarketHoursResponse getMarketHoursAt(ZonedDateTime now) {
        log.debug("Checking market hours: now={}", now);
        boolean open = isMarketOpen(now);
        String nextOpenDate = null;
        if (!open) {
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
        log.debug("Fetching all instruments");
        String status = isMarketOpen(ZonedDateTime.now(ZoneId.of(timezone))) ? "OPEN" : "CLOSED";
        return instrumentRepository.findAllByOrderBySymbolAsc()
                .stream()
                .map(instrument -> {
                    InstrumentSummaryResponse base = instrumentSummaryMapper.toSummary(instrument);
                    return enrichSummaryResponse(base,
                            instrument.getLastPrice(),
                            instrument.getPreviousClose(),
                            status);
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<InstrumentSearchResponse> searchInstruments(String q, int limit) {
        String normalised = q.trim();
        int effectiveLimit = Math.min(Math.max(limit, 1), SEARCH_MAX_LIMIT);
        String cacheKey = SEARCH_CACHE_KEY_PREFIX + normalised.toLowerCase();
        log.debug("Searching instruments: q={}, limit={}", normalised, effectiveLimit);

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof List<?> list
                && (list.isEmpty() || list.get(0) instanceof InstrumentSearchResponse)) {
            log.debug("Search cache hit: q={}", normalised);
            return list.stream()
                    .filter(InstrumentSearchResponse.class::isInstance)
                    .map(InstrumentSearchResponse.class::cast)
                    .limit(effectiveLimit)
                    .toList();
        }

        String qUpper = normalised.toUpperCase();
        List<InstrumentSearchResponse> results = instrumentRepository
                .findAllByTradableIsTrueAndTradingHaltedIsFalseOrderBySymbolAsc()
                .stream()
                .filter(i -> matchesQuery(i, qUpper))
                .sorted(Comparator.comparingInt(i -> relevanceScore(i, qUpper)))
                .map(instrumentSearchMapper::toSearchResponse)
                .toList();

        redisTemplate.opsForValue().set(cacheKey, results, SEARCH_CACHE_TTL);
        log.debug("Search cache populated: q={}, hits={}", normalised, results.size());

        return results.stream().limit(effectiveLimit).toList();
    }

    private boolean matchesQuery(Instrument instrument, String qUpper) {
        if (instrument.getSymbol().toUpperCase().contains(qUpper)) return true;
        if (instrument.getCompanyName() != null
                && instrument.getCompanyName().toUpperCase().contains(qUpper)) return true;
        if (instrument.getCusip() != null
                && instrument.getCusip().toUpperCase().startsWith(qUpper)) return true;
        if (instrument.getSecNumber() != null
                && instrument.getSecNumber().equalsIgnoreCase(qUpper)) return true;
        return false;
    }

    private int relevanceScore(Instrument instrument, String qUpper) {
        if (instrument.getSymbol().equalsIgnoreCase(qUpper)) return 0;
        if (instrument.getCusip() != null && instrument.getCusip().equalsIgnoreCase(qUpper)) return 1;
        if (instrument.getSecNumber() != null && instrument.getSecNumber().equalsIgnoreCase(qUpper)) return 2;
        if (instrument.getSymbol().toUpperCase().startsWith(qUpper)) return 3;
        if (instrument.getCusip() != null && instrument.getCusip().toUpperCase().startsWith(qUpper)) return 4;
        if (instrument.getCompanyName() != null
                && instrument.getCompanyName().toUpperCase().contains(qUpper)) return 5;
        return 6;
    }

    public boolean isMarketOpen(ZonedDateTime now) {
        if (bypassMarketHours) {
            return true;
        }
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

    // --- private enrichment helpers ---

    private InstrumentResponse enrichInstrumentResponse(InstrumentResponse base,
            BigDecimal lastPrice, BigDecimal previousClose) {
        String status = isMarketOpen(ZonedDateTime.now(ZoneId.of(timezone))) ? "OPEN" : "CLOSED";
        return base.toBuilder()
                .change(computeChange(lastPrice, previousClose))
                .changePct(computeChangePct(lastPrice, previousClose))
                .marketStatus(status)
                .build();
    }

    private InstrumentSummaryResponse enrichSummaryResponse(InstrumentSummaryResponse base,
            BigDecimal lastPrice, BigDecimal previousClose, String marketStatus) {
        return base.toBuilder()
                .change(computeChange(lastPrice, previousClose))
                .changePct(computeChangePct(lastPrice, previousClose))
                .marketStatus(marketStatus)
                .build();
    }

    String computeChange(BigDecimal lastPrice, BigDecimal previousClose) {
        if (lastPrice == null || previousClose == null) {
            return "0";
        }
        return lastPrice.subtract(previousClose).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    String computeChangePct(BigDecimal lastPrice, BigDecimal previousClose) {
        if (lastPrice == null || previousClose == null
                || previousClose.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return lastPrice.subtract(previousClose)
                .divide(previousClose, 4, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
