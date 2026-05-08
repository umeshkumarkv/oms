package com.brokerdealer.orderservice.exception;

public class ValidationException extends RuntimeException {
    private final String errorCode;
    private final String fieldName;

    public ValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.fieldName = null;
    }

    public ValidationException(String fieldName, String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.fieldName = fieldName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getFieldName() {
        return fieldName;
    }
}
