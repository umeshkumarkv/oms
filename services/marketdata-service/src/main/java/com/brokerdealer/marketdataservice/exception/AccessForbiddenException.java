package com.brokerdealer.marketdataservice.exception;

import lombok.Getter;

@Getter
public class AccessForbiddenException extends RuntimeException {

    private final String errorCode;

    public AccessForbiddenException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
