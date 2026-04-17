package com.brokerdealer.orderservice.domain;

public enum OrderStatus {
    NEW,
    VALIDATED,
    RISK_CHECK_PASSED,
    RISK_REJECTED,
    ACCEPTED,
    SENT_TO_EXECUTION,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED,
    EXPIRED
}
