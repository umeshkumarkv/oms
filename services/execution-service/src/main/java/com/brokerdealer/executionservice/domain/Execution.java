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
@Table(name = "executions", schema = "execution_schema")
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    private OrderSide side;

    @Column(name = "executed_qty", nullable = false)
    private Integer executedQty;

    @Column(name = "executed_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal executedPrice;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;
}
