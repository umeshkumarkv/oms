package com.brokerdealer.orderservice.exception;

public class DuplicateOrderException extends SemanticValidationException {
    public DuplicateOrderException() {
        super("DUPLICATE_ORDER", "Duplicate order detected within 30 seconds");
    }
}
