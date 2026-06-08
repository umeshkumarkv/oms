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

    @Column(name = "day_high", precision = 19, scale = 4)
    private BigDecimal dayHigh;

    @Column(name = "day_low", precision = 19, scale = 4)
    private BigDecimal dayLow;

    @Column(name = "previous_close", precision = 19, scale = 4)
    private BigDecimal previousClose;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "seed_last_price", precision = 19, scale = 4)
    private BigDecimal seedLastPrice;

    @Column(name = "seed_bid_price", precision = 19, scale = 4)
    private BigDecimal seedBidPrice;

    @Column(name = "seed_ask_price", precision = 19, scale = 4)
    private BigDecimal seedAskPrice;

    @Column(name = "open_price", precision = 19, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "cusip", length = 9)
    private String cusip;

    @Column(name = "sec_number", length = 20)
    private String secNumber;

    @Column(name = "exchange", length = 20)
    private String exchange;
}
