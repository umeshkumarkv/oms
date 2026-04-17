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
@Table(name = "instruments", schema = "market_schema")
public class Instrument {

    @Id
    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "bid_price", precision = 18, scale = 4)
    private BigDecimal bidPrice;

    @Column(name = "ask_price", precision = 18, scale = 4)
    private BigDecimal askPrice;

    @Column(name = "last_price", precision = 18, scale = 4)
    private BigDecimal lastPrice;

    @Column(name = "tradable", nullable = false)
    private Boolean tradable;

    @Column(name = "trading_halted", nullable = false)
    private Boolean tradingHalted;

    @Column(name = "min_quantity")
    private Integer minQuantity;

    @Column(name = "max_quantity")
    private Integer maxQuantity;

    @Column(name = "data_timestamp")
    private Instant dataTimestamp;
}