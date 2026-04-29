package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.repository.InstrumentRepository;
import com.brokerdealer.marketdataservice.websocket.MarketDataWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceTickService {

    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final String CACHE_KEY_PREFIX = "market:price:";

    private final InstrumentRepository instrumentRepository;
    private final MarketDataService marketDataService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MarketDataWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    private final ScenarioService scenarioService;

    @Value("${market.simulation.bypass-market-hours:false}")
    private boolean bypassMarketHours;

    private volatile LocalDate lastResetDate;

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/New_York"));
        if (!bypassMarketHours && !marketDataService.isMarketOpen(now)) {
            return;
        }

        LocalDate today = now.toLocalDate();
        boolean isFirstTickOfDay = !today.equals(lastResetDate);
        if (isFirstTickOfDay) {
            lastResetDate = today;
            log.info("Price simulation daily reset: date={}", today);
        }

        List<Instrument> instruments = instrumentRepository.findAll();
        for (Instrument instrument : instruments) {
            if (Boolean.TRUE.equals(instrument.getTradingHalted())) {
                continue;
            }
            tickInstrument(instrument, isFirstTickOfDay);
        }
    }

    void tickInstrument(Instrument instrument, boolean resetDay) {
        BigDecimal currentPrice = instrument.getLastPrice();

        BigDecimal newPrice;
        if (scenarioService.hasActiveScenario(instrument.getSymbol())) {
            BigDecimal increment = scenarioService.applyScenarioTick(instrument.getSymbol());
            if (increment == null) {
                // scenario just completed on this call — fall through to random walk for this tick
                newPrice = computeRandomWalkPrice(currentPrice);
            } else {
                newPrice = currentPrice.add(increment).setScale(4, RoundingMode.HALF_UP);
                if (newPrice.compareTo(MIN_PRICE) < 0) {
                    newPrice = MIN_PRICE;
                }
            }
        } else {
            newPrice = computeRandomWalkPrice(currentPrice);
        }

        // recompute bid/ask preserving the existing spread
        BigDecimal spread = instrument.getAskPrice().subtract(instrument.getBidPrice());
        BigDecimal halfSpread = spread.divide(TWO, 4, RoundingMode.HALF_UP);
        instrument.setBidPrice(newPrice.subtract(halfSpread).setScale(4, RoundingMode.HALF_UP));
        instrument.setAskPrice(newPrice.add(halfSpread).setScale(4, RoundingMode.HALF_UP));
        instrument.setLastPrice(newPrice);
        instrument.setDataTimestamp(Instant.now());

        // day high / low / previousClose reset on first tick of new trading day
        if (resetDay || instrument.getDayHigh() == null) {
            instrument.setPreviousClose(currentPrice);
            instrument.setDayHigh(newPrice);
            instrument.setDayLow(newPrice);
            instrument.setVolume(0L);
        } else {
            if (newPrice.compareTo(instrument.getDayHigh()) > 0) {
                instrument.setDayHigh(newPrice);
            }
            if (newPrice.compareTo(instrument.getDayLow()) < 0) {
                instrument.setDayLow(newPrice);
            }
        }

        // volume: add random 500–5000 per tick
        long volumeDelta = 500L + ThreadLocalRandom.current().nextLong(4501L);
        instrument.setVolume((instrument.getVolume() == null ? 0L : instrument.getVolume()) + volumeDelta);

        instrumentRepository.save(instrument);

        // invalidate Redis cache so next read fetches fresh data
        redisTemplate.delete(CACHE_KEY_PREFIX + instrument.getSymbol());
        log.debug("Ticked: symbol={} newPrice={}", instrument.getSymbol(), newPrice.toPlainString());

        broadcastTick(instrument);
    }

    private BigDecimal computeRandomWalkPrice(BigDecimal currentPrice) {
        boolean up = ThreadLocalRandom.current().nextInt(100) < 52;
        BigDecimal changePct = BigDecimal.valueOf(1 + ThreadLocalRandom.current().nextInt(5), 3);
        BigDecimal change = currentPrice.multiply(changePct).setScale(4, RoundingMode.HALF_UP);
        BigDecimal newPrice = up ? currentPrice.add(change) : currentPrice.subtract(change);
        return newPrice.compareTo(MIN_PRICE) < 0 ? MIN_PRICE : newPrice;
    }

    private void broadcastTick(Instrument instrument) {
        try {
            String previousClose = instrument.getPreviousClose() != null
                    ? instrument.getPreviousClose().toPlainString()
                    : instrument.getLastPrice().toPlainString();
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", "MARKET_DATA_TICK",
                    "symbol", instrument.getSymbol(),
                    "bidPrice", instrument.getBidPrice().toPlainString(),
                    "askPrice", instrument.getAskPrice().toPlainString(),
                    "lastPrice", instrument.getLastPrice().toPlainString(),
                    "previousClose", previousClose,
                    "dataTimestamp", instrument.getDataTimestamp().toString()
            ));
            webSocketHandler.broadcast(json);
        } catch (Exception e) {
            log.warn("Failed to broadcast tick: symbol={} error={}", instrument.getSymbol(), e.getMessage());
        }
    }
}
