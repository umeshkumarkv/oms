package com.brokerdealer.marketdataservice.service;

import java.math.BigDecimal;

public record ScenarioState(String scenarioId, String symbol, BigDecimal incrementPerTick, int remainingTicks) {

    public ScenarioState withOneTickApplied() {
        return new ScenarioState(scenarioId, symbol, incrementPerTick, remainingTicks - 1);
    }

    public boolean isComplete() {
        return remainingTicks <= 0;
    }
}
