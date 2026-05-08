package com.brokerdealer.riskservice.exception;

public class RuleNotFoundException extends RuntimeException {

    private final String errorCode;

    public RuleNotFoundException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
