package com.brokerdealer.orderservice.repository;

import com.brokerdealer.orderservice.domain.Order;
import com.brokerdealer.orderservice.domain.OrderStatus;
import com.brokerdealer.orderservice.domain.TimeInForce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

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
}
