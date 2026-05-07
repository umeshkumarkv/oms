package com.brokerdealer.orderservice.exception;

public class ClientInactiveException extends SemanticValidationException {
    public ClientInactiveException(String clientId) {
        super("CLIENT_INACTIVE", "Client account is not active: " + clientId);
    }
}
