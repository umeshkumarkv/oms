package com.brokerdealer.orderservice.exception;

public class ClientNotFoundException extends SemanticValidationException {
    public ClientNotFoundException(String clientId) {
        super("CLIENT_NOT_FOUND", "Client not found: " + clientId);
    }
}
