package com.brokerdealer.orderservice.exception;

public class SymbolHaltedException extends SemanticValidationException {
    public SymbolHaltedException(String symbol) {
        super("SYMBOL_HALTED", "Trading is halted for symbol: " + symbol);
    }
}
