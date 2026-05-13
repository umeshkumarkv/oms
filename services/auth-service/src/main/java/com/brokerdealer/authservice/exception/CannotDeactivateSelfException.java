package com.brokerdealer.authservice.exception;

public class CannotDeactivateSelfException extends RuntimeException {

    private final String errorCode;

    public CannotDeactivateSelfException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
