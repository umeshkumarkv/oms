package com.brokerdealer.executionservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pending_order_executions", schema = "execution_schema")
public class PendingOrderExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 10)
    private OrderType orderType;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "limit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal limitPrice;

    @Column(name = "last_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal lastPrice;

    @Column(name = "order_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal orderValue;

    @Column(name = "fill_or_kill", nullable = false)
    private boolean fillOrKill = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private PendingStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
