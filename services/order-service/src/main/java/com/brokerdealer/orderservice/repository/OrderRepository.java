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
    Object[] computeAvgMetricsByClientId(@Param("clientId") UUID clientId, @Param("cutoff") Instant cutoff);
}
