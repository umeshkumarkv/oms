package com.brokerdealer.clientservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "clients", schema = "client_schema")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClientStatus status;

    @Column(name = "trading_enabled", nullable = false)
    private Boolean tradingEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus;

    @Column(name = "kyc_expiry_date")
    private LocalDate kycExpiryDate;

    @Column(name = "margin_call_active", nullable = false)
    private Boolean marginCallActive;

    @Column(name = "cash_balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal cashBalance;

    @Column(name = "reserved_cash", nullable = false, precision = 18, scale = 2)
    private BigDecimal reservedCash;

    @Column(name = "trading_limit", precision = 18, scale = 2)
    private BigDecimal tradingLimit;

    @Column(name = "daily_order_count", nullable = false)
    private Integer dailyOrderCount;

    @Column(name = "daily_order_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal dailyOrderValue;

    @Column(name = "daily_order_limit", precision = 18, scale = 2)
    private BigDecimal dailyOrderLimit;

    @Column(name = "max_open_orders")
    private Integer maxOpenOrders;

    @Column(name = "avg_order_qty")
    private Integer avgOrderQty;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}