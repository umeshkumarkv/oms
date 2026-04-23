package com.brokerdealer.orderservice.exception;

public class SelfTradeException extends RuntimeException {
    public SelfTradeException(String message) {
        super(message);
    }
}
