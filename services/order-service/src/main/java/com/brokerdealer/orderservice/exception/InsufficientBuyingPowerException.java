package com.brokerdealer.orderservice.exception;

public class InsufficientBuyingPowerException extends RuntimeException {
    public InsufficientBuyingPowerException(String message) {
        super(message);
    }
}
