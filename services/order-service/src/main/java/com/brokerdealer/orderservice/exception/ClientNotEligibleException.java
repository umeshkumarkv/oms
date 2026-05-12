package com.brokerdealer.orderservice.exception;

public class ClientNotEligibleException extends SemanticValidationException {
    public ClientNotEligibleException(String reason) {
        super("CLIENT_NOT_ELIGIBLE", "Client is not eligible to trade: " + reason);
    }
}
