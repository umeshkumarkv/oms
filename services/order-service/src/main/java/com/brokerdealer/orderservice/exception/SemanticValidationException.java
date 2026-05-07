package com.brokerdealer.orderservice.exception;

public class SemanticValidationException extends RuntimeException {
    private final String errorCode;

    public SemanticValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
