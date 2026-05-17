package com.brokerdealer.riskservice.exception;

public class SelfApprovalNotAllowedException extends RuntimeException {

    private final String errorCode;

    public SelfApprovalNotAllowedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
