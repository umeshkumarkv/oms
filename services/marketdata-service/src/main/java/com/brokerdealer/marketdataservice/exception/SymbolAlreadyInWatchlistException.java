package com.brokerdealer.marketdataservice.exception;

import lombok.Getter;

@Getter
public class SymbolAlreadyInWatchlistException extends RuntimeException {

    private final String errorCode;

    public SymbolAlreadyInWatchlistException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
