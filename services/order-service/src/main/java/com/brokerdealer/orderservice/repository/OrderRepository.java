package com.brokerdealer.orderservice.repository;

import com.brokerdealer.orderservice.domain.Order;
import com.brokerdealer.orderservice.domain.OrderSide;
import com.brokerdealer.orderservice.domain.OrderStatus;
import com.brokerdealer.orderservice.domain.TimeInForce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    List<Order> findByTimeInForceAndStatusIn(TimeInForce timeInForce, List<OrderStatus> statuses);

    List<Order> findByTimeInForceAndExpiryDateAndStatusIn(TimeInForce timeInForce, LocalDate expiryDate,
                                                          List<OrderStatus> statuses);

    List<Order> findByTimeInForceAndExpiryDateBeforeAndStatusIn(TimeInForce timeInForce, LocalDate expiryDate,
                                                                List<OrderStatus> statuses);

    // Returns open orders on opposite side for self-trade / crossing detection
    List<Order> findByClientIdAndSymbolAndSideAndStatusIn(UUID clientId, String symbol,
                                                          OrderSide side, List<OrderStatus> statuses);

    // Count orders submitted today (UTC) for a client
    @Query("SELECT COUNT(o) FROM Order o WHERE o.clientId = :clientId AND o.createdAt >= :startOfDay")
    long countDailyOrdersByClientId(@Param("clientId") UUID clientId, @Param("startOfDay") Instant startOfDay);

    // Average quantity and average order value over the last 90 days for a client
    @Query("SELECT AVG(CAST(o.quantity AS java.lang.Double)), AVG(o.orderValue) " +
           "FROM Order o WHERE o.clientId = :clientId AND o.createdAt >= :cutoff")
    List<Object[]> computeAvgMetricsByClientId(@Param("clientId") UUID clientId, @Param("cutoff") Instant cutoff);

    // Sum of quantity committed to open (non-terminal) SELL orders for a client+symbol.
    // Used by Level 2 check #20 to compute availableToSell = totalHoldings - committed.
    @Query("SELECT COALESCE(SUM(o.quantity), 0) FROM Order o " +
           "WHERE o.clientId = :clientId AND o.symbol = :symbol " +
           "AND o.side = com.brokerdealer.orderservice.domain.OrderSide.SELL " +
           "AND o.status IN :openStatuses")
    long sumOpenSellQuantity(@Param("clientId") UUID clientId,
                             @Param("symbol") String symbol,
                             @Param("openStatuses") List<OrderStatus> openStatuses);

    // Sum of orderValue for open (non-terminal) BUY orders for a client.
    // Used by Level 2 check #13 to compute availableCash = cashBalance - committed.
    @Query("SELECT SUM(o.orderValue) FROM Order o " +
           "WHERE o.clientId = :clientId " +
           "AND o.side = com.brokerdealer.orderservice.domain.OrderSide.BUY " +
           "AND o.status IN :openStatuses")
    BigDecimal sumOpenBuyOrderValues(@Param("clientId") UUID clientId,
                                      @Param("openStatuses") List<OrderStatus> openStatuses);

    // Count orders grouped by status for a given ET date range
    @Query("SELECT o.status, COUNT(o) FROM Order o " +
           "WHERE o.createdAt >= :startOfDay AND o.createdAt < :endOfDay " +
           "GROUP BY o.status")
    List<Object[]> countByStatusForDateRange(@Param("startOfDay") Instant startOfDay,
                                              @Param("endOfDay") Instant endOfDay);

    // Dashboard — distinct client IDs whose total orderValue today exceeds the threshold.
    // Native query required: JPQL does not support subqueries in FROM clause.
    @Query(value = """
            SELECT client_id FROM order_schema.orders
            WHERE created_at >= :startOfDay AND created_at < :endOfDay
            GROUP BY client_id
            HAVING SUM(order_value) > :threshold
            """, nativeQuery = true)
    List<java.util.UUID> findClientIdsWithHighNotionalToday(@Param("startOfDay") Instant startOfDay,
                                                            @Param("endOfDay") Instant endOfDay,
                                                            @Param("threshold") BigDecimal threshold);

    // Dashboard — count of orders in a given status within today's ET date range
    @Query("SELECT COUNT(o) FROM Order o " +
           "WHERE o.status = :status " +
           "AND o.createdAt >= :startOfDay AND o.createdAt < :endOfDay")
    long countByStatusAndDateRange(@Param("status") OrderStatus status,
                                   @Param("startOfDay") Instant startOfDay,
                                   @Param("endOfDay") Instant endOfDay);

    // Dashboard — count of active orders (any status in list) whose orderValue exceeds the threshold
    @Query("SELECT COUNT(o) FROM Order o " +
           "WHERE o.status IN :statuses AND o.orderValue > :threshold")
    long countActiveOrdersByStatusesAndMinOrderValue(@Param("statuses") List<OrderStatus> statuses,
                                                     @Param("threshold") BigDecimal threshold);

    // Dashboard — count of orders in a given status whose updatedAt is before a cutoff instant
    @Query("SELECT COUNT(o) FROM Order o " +
           "WHERE o.status = :status AND o.updatedAt < :cutoff")
    long countByStatusAndUpdatedAtBefore(@Param("status") OrderStatus status,
                                         @Param("cutoff") Instant cutoff);

    // Dashboard summary — total order count within ET date range (no status filter)
    @Query("SELECT COUNT(o) FROM Order o " +
           "WHERE o.createdAt >= :startOfDay AND o.createdAt < :endOfDay")
    long countAllOrdersForDateRange(@Param("startOfDay") Instant startOfDay,
                                    @Param("endOfDay") Instant endOfDay);

    // Dashboard summary — sum of pre-computed orderValue for given statuses within ET date range
    // Used for turnoverToday (FILLED + PARTIALLY_FILLED) and filledCount derivation
    @Query("SELECT COALESCE(SUM(o.orderValue), 0) FROM Order o " +
           "WHERE o.status IN :statuses " +
           "AND o.createdAt >= :startOfDay AND o.createdAt < :endOfDay")
    BigDecimal sumOrderValueByStatusesForDateRange(@Param("statuses") List<OrderStatus> statuses,
                                                   @Param("startOfDay") Instant startOfDay,
                                                   @Param("endOfDay") Instant endOfDay);

    // Dashboard summary — count of orders for given statuses within ET date range
    // Used for filledCount numerator in fillRate calculation
    @Query("SELECT COUNT(o) FROM Order o " +
           "WHERE o.status IN :statuses " +
           "AND o.createdAt >= :startOfDay AND o.createdAt < :endOfDay")
    long countOrdersByStatusesForDateRange(@Param("statuses") List<OrderStatus> statuses,
                                           @Param("startOfDay") Instant startOfDay,
                                           @Param("endOfDay") Instant endOfDay);

    // Dashboard summary — sum of pre-computed orderValue for given sides within ET date range
    // Used for buySideNotional (BUY) and sellSideNotional (SELL + SHORT_SELL)
    @Query("SELECT COALESCE(SUM(o.orderValue), 0) FROM Order o " +
           "WHERE o.side IN :sides " +
           "AND o.createdAt >= :startOfDay AND o.createdAt < :endOfDay")
    BigDecimal sumOrderValueBySidesForDateRange(@Param("sides") List<OrderSide> sides,
                                                @Param("startOfDay") Instant startOfDay,
                                                @Param("endOfDay") Instant endOfDay);
}
