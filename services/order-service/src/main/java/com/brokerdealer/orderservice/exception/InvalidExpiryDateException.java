package com.brokerdealer.orderservice.exception;

public class InvalidExpiryDateException extends SemanticValidationException {
    public InvalidExpiryDateException(String reason) {
        super("INVALID_GTD_DATE", "Invalid expiry date for GTC order: " + reason);
    }
}
