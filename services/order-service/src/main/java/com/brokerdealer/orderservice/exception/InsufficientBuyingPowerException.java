package com.brokerdealer.orderservice.exception;

public class InsufficientBuyingPowerException extends SemanticValidationException {
    public InsufficientBuyingPowerException() {
        super("INSUFFICIENT_BUYING_POWER", "Insufficient buying power for this order");
    }
}
