package com.brokerdealer.orderservice.exception;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    private final String errorCode = "ORDER_NOT_FOUND";

    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
    }

    public String getErrorCode() {
        return errorCode;
    }
}
