package com.brokerdealer.orderservice.exception;

public class SymbolHaltedException extends RuntimeException {
    public SymbolHaltedException(String message) {
        super(message);
    }
}
