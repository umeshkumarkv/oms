package com.brokerdealer.orderservice.exception;

public class ServiceUnavailableException extends RuntimeException {
    private final String errorCode;

    public ServiceUnavailableException(String serviceName) {
        super("Downstream service unavailable: " + serviceName);
        this.errorCode = "SERVICE_UNAVAILABLE";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
