package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.config.ScenarioConfig;
import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.dto.request.ScenarioRequest;
import com.brokerdealer.marketdataservice.dto.response.ScenarioResponse;
import com.brokerdealer.marketdataservice.exception.ScenarioNotFoundException;
import com.brokerdealer.marketdataservice.exception.SymbolHaltedException;
import com.brokerdealer.marketdataservice.exception.SymbolNotFoundException;
import com.brokerdealer.marketdataservice.repository.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScenarioService {

    private final ScenarioConfig scenarioConfig;
    private final InstrumentRepository instrumentRepository;

    private final ConcurrentHashMap<String, ScenarioState> activeScenarios = new ConcurrentHashMap<>();

    public ScenarioResponse startScenario(ScenarioRequest request) {
        String scenarioId = request.getScenarioId();
        String symbol = request.getSymbol().toUpperCase();

        ScenarioConfig.ScenarioDefinition definition = scenarioConfig.getItems().get(scenarioId);
        if (definition == null) {
            throw new ScenarioNotFoundException("SCENARIO_NOT_FOUND", "Scenario not found: " + scenarioId);
        }

        Instrument instrument = instrumentRepository.findById(symbol)
                .orElseThrow(() -> new SymbolNotFoundException("SYMBOL_NOT_FOUND", "Symbol not found: " + symbol));

        if (Boolean.TRUE.equals(instrument.getTradingHalted())) {
            throw new SymbolHaltedException("SYMBOL_HALTED",
                    "Symbol is halted and cannot have a scenario triggered: " + symbol);
        }

        BigDecimal currentPrice = instrument.getLastPrice();
        BigDecimal totalMovement = currentPrice
                .multiply(definition.getDirectionPct().abs())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        boolean isPositive = definition.getDirectionPct().compareTo(BigDecimal.ZERO) > 0;
        BigDecimal rawIncrement = totalMovement
                .divide(BigDecimal.valueOf(definition.getTicks()), 4, RoundingMode.HALF_UP);
        BigDecimal incrementPerTick = isPositive ? rawIncrement : rawIncrement.negate();

        ScenarioState state = new ScenarioState(scenarioId, symbol, incrementPerTick, definition.getTicks());
        activeScenarios.put(symbol, state);

        log.info("Scenario started: scenarioId={} symbol={} ticks={} incrementPerTick={}",
                scenarioId, symbol, definition.getTicks(), incrementPerTick.toPlainString());

        return ScenarioResponse.builder()
                .status("SCENARIO_STARTED")
                .scenarioId(scenarioId)
                .symbol(symbol)
                .expectedDuration(definition.getTicks() + "s")
                .build();
    }

    public boolean hasActiveScenario(String symbol) {
        return activeScenarios.containsKey(symbol);
    }

    /**
     * Applies one tick of the active scenario for the given symbol.
     * Thread-safe via ConcurrentHashMap.compute — removes the state when ticks are exhausted.
     *
     * @return the signed increment to apply, or null if no active scenario
     */
    public BigDecimal applyScenarioTick(String symbol) {
        BigDecimal[] appliedIncrement = {null};
        activeScenarios.compute(symbol, (k, state) -> {
            if (state == null) return null;
            appliedIncrement[0] = state.incrementPerTick();
            ScenarioState updated = state.withOneTickApplied();
            if (updated.isComplete()) {
                log.info("Scenario completed: scenarioId={} symbol={}", state.scenarioId(), symbol);
                return null;
            }
            return updated;
        });
        return appliedIncrement[0];
    }
}
