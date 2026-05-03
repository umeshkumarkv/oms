package com.brokerdealer.marketdataservice.exception;

import lombok.Getter;

@Getter
public class ScenarioAlreadyActiveException extends RuntimeException {

    private final String errorCode;

    public ScenarioAlreadyActiveException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
