package com.brokerdealer.orderservice.exception;

public class MarketClosedException extends SemanticValidationException {
    public MarketClosedException() {
        super("MARKET_CLOSED", "Market is currently closed. Trading hours are 09:30-16:00 ET Monday-Friday.");
    }
}
