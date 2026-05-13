package com.brokerdealer.marketdataservice.exception;

import lombok.Getter;

@Getter
public class SymbolNotInWatchlistException extends RuntimeException {

    private final String errorCode;

    public SymbolNotInWatchlistException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
