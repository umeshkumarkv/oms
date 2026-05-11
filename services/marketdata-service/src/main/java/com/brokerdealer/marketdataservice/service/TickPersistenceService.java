package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.repository.TickRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Persists each market tick to the ticks table asynchronously on a virtual thread
 * so the DB write never slows the 1-second WebSocket broadcast on the scheduler thread.
 *
 * Upsert semantics: if the same (symbol, tick_time) arrives twice the row is
 * refreshed rather than rejected — fully idempotent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TickPersistenceService {

    private final TickRepository tickRepository;

    @Async
    @Transactional
    public void saveTick(String     symbol,
                         BigDecimal price,
                         BigDecimal bid,
                         BigDecimal ask,
                         Long       volume) {
        try {
            // Truncate to second — one row per second per symbol
            Instant tickTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);

            tickRepository.upsertTick(symbol, price, bid, ask, volume, tickTime);
            log.debug("Tick persisted: symbol={} tickTime={}", symbol, tickTime);
        } catch (Exception e) {
            log.warn("Tick persistence failed: symbol={} error={}", symbol, e.getMessage());
        }
    }
}
