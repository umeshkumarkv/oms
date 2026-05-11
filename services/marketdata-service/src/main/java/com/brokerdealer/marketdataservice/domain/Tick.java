package com.brokerdealer.marketdataservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ticks", schema = "market_schema")
public class Tick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 10)
    private String symbol;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "bid", precision = 19, scale = 4)
    private BigDecimal bid;

    @Column(name = "ask", precision = 19, scale = 4)
    private BigDecimal ask;

    @Column(name = "volume")
    private Long volume;

    // Stored as UTC — TIMESTAMPTZ
    @Column(name = "tick_time", nullable = false)
    private Instant tickTime;
}
