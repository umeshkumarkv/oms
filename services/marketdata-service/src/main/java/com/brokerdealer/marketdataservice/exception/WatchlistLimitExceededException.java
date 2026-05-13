package com.brokerdealer.marketdataservice.exception;

import lombok.Getter;

@Getter
public class WatchlistLimitExceededException extends RuntimeException {

    private final String errorCode;

    public WatchlistLimitExceededException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
