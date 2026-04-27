package com.brokerdealer.marketdataservice.exception;

import lombok.Getter;

@Getter
public class SymbolNotFoundException extends RuntimeException {

    private final String errorCode;

    public SymbolNotFoundException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
