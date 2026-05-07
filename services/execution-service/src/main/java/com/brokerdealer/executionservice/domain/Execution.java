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

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    private OrderSide side;

    @Column(name = "executed_qty", nullable = false)
    private Integer executedQty;

    @Column(name = "executed_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal executedPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "fill_type", nullable = false, length = 10)
    private FillType fillType;

    @Column(name = "fill_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal fillValue;

    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    @Column(name = "market_price_at_fill", nullable = false, precision = 19, scale = 4)
    private BigDecimal marketPriceAtFill;

    @Column(name = "execution_venue", nullable = false, length = 50)
    private String executionVenue;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;
}
