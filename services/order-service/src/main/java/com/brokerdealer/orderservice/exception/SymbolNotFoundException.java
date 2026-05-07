package com.brokerdealer.orderservice.exception;

public class SymbolNotFoundException extends SemanticValidationException {
    public SymbolNotFoundException(String symbol) {
        super("SYMBOL_NOT_FOUND", "Symbol not found or not tradable: " + symbol);
    }
}
