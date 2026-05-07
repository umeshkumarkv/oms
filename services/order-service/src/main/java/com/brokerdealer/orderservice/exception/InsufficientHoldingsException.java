package com.brokerdealer.orderservice.exception;

public class InsufficientHoldingsException extends SemanticValidationException {
    public InsufficientHoldingsException() {
        super("INSUFFICIENT_HOLDINGS", "Insufficient holdings for SELL order");
    }
}
