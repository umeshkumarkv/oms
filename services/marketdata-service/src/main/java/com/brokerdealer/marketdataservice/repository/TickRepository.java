package com.brokerdealer.marketdataservice.repository;

import com.brokerdealer.marketdataservice.domain.Tick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public interface TickRepository extends JpaRepository<Tick, Long> {

    /**
     * Upsert one tick row.  On duplicate (symbol, tick_time) the price/bid/ask/volume
     * columns are refreshed so retries from the async executor are idempotent.
     */
    @Modifying
    @Query(value = """
            INSERT INTO market_schema.ticks (symbol, price, bid, ask, volume, tick_time)
            VALUES (:symbol, :price, :bid, :ask, :volume, CAST(:tickTime AS TIMESTAMPTZ))
            ON CONFLICT (symbol, tick_time) DO UPDATE
               SET price  = EXCLUDED.price,
                   bid    = EXCLUDED.bid,
                   ask    = EXCLUDED.ask,
                   volume = EXCLUDED.volume
            """, nativeQuery = true)
    void upsertTick(@Param("symbol")   String     symbol,
                    @Param("price")    BigDecimal price,
                    @Param("bid")      BigDecimal bid,
                    @Param("ask")      BigDecimal ask,
                    @Param("volume")   Long       volume,
                    @Param("tickTime") Instant    tickTime);

    /**
     * Return all ticks for {@code symbol} in the range [from, to),
     * ordered oldest-first.  {@code to} is exclusive (current-minute boundary).
     */
    @Query("SELECT t FROM Tick t WHERE t.symbol = :symbol " +
           "AND t.tickTime >= :from AND t.tickTime < :to " +
           "ORDER BY t.tickTime ASC")
    List<Tick> findHistory(@Param("symbol") String  symbol,
                           @Param("from")   Instant from,
                           @Param("to")     Instant to);
}
