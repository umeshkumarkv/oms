package com.brokerdealer.orderservice.exception;

import lombok.Getter;

@Getter
public class OrderAccessDeniedException extends RuntimeException {

    private final String errorCode;

    public OrderAccessDeniedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}