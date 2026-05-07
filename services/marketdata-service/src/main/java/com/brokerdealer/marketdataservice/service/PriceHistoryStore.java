package com.brokerdealer.marketdataservice.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PriceHistoryStore {

    static final int MAX_HISTORY = 20;

    private final ConcurrentHashMap<String, LinkedList<BigDecimal>> history = new ConcurrentHashMap<>();

    public synchronized void addPrice(String symbol, BigDecimal price) {
        LinkedList<BigDecimal> list = history.computeIfAbsent(symbol, k -> new LinkedList<>());
        list.addLast(price);
        if (list.size() > MAX_HISTORY) {
            list.pollFirst();
        }
    }

    public synchronized List<String> getPriceHistory(String symbol) {
        LinkedList<BigDecimal> list = history.get(symbol);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(list).stream()
                .map(BigDecimal::toPlainString)
                .toList();
    }

    public synchronized void reset(String symbol) {
        history.remove(symbol);
    }
}
