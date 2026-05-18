package com.brokerdealer.riskservice.exception;

public class ChangeAlreadyReviewedException extends RuntimeException {

    private final String errorCode;

    public ChangeAlreadyReviewedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
