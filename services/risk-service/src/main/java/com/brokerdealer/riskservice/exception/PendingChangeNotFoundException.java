package com.brokerdealer.riskservice.exception;

public class PendingChangeNotFoundException extends RuntimeException {

    private final String errorCode;

    public PendingChangeNotFoundException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
