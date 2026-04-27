package com.brokerdealer.clientservice.exception;

import lombok.Getter;

@Getter
public class ClientNotFoundException extends RuntimeException {

    private final String errorCode;

    public ClientNotFoundException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
