package com.brokerdealer.clientservice.exception;

import lombok.Getter;

@Getter
public class InvalidReservationException extends RuntimeException {

    private final String errorCode;

    public InvalidReservationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
