package com.brokerdealer.orderservice.exception;

public class MarketClosedException extends RuntimeException {
    public MarketClosedException(String message) {
        super(message);
    }
}
