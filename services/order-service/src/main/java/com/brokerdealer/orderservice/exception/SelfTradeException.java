package com.brokerdealer.orderservice.exception;

public class SelfTradeException extends SemanticValidationException {
    public SelfTradeException() {
        super("SELF_TRADE", "Order would create a self-trade");
    }
}
