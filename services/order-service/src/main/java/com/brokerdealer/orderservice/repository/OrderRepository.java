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

    // Count orders grouped by status for a given ET date range
    @Query("SELECT o.status, COUNT(o) FROM Order o " +
           "WHERE o.createdAt >= :startOfDay AND o.createdAt < :endOfDay " +
           "GROUP BY o.status")
    List<Object[]> countByStatusForDateRange(@Param("startOfDay") Instant startOfDay,
                                              @Param("endOfDay") Instant endOfDay);
}
