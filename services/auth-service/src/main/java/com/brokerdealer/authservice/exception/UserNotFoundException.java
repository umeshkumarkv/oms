package com.brokerdealer.authservice.exception;

public class UserNotFoundException extends RuntimeException {

    private final String errorCode;

    public UserNotFoundException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
