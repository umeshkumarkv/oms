package com.brokerdealer.authservice.exception;

public class UsernameTakenException extends RuntimeException {

    private final String errorCode;

    public UsernameTakenException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
