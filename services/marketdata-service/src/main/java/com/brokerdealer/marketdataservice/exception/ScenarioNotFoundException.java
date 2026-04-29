package com.brokerdealer.marketdataservice.exception;

import lombok.Getter;

@Getter
public class ScenarioNotFoundException extends RuntimeException {

    private final String errorCode;

    public ScenarioNotFoundException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
