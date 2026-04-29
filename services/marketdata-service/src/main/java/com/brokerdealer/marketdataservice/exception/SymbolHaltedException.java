package com.brokerdealer.marketdataservice.exception;

import lombok.Getter;

@Getter
public class SymbolHaltedException extends RuntimeException {

    private final String errorCode;

    public SymbolHaltedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
