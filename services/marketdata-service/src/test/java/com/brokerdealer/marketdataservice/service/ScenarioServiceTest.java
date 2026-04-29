package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.config.ScenarioConfig;
import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.dto.request.ScenarioRequest;
import com.brokerdealer.marketdataservice.dto.response.ScenarioResponse;
import com.brokerdealer.marketdataservice.exception.ScenarioNotFoundException;
import com.brokerdealer.marketdataservice.exception.SymbolHaltedException;
import com.brokerdealer.marketdataservice.exception.SymbolNotFoundException;
import com.brokerdealer.marketdataservice.repository.InstrumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScenarioServiceTest {

    @Mock
    private ScenarioConfig scenarioConfig;

    @Mock
    private InstrumentRepository instrumentRepository;

    @InjectMocks
    private ScenarioService scenarioService;

    private Map<String, ScenarioConfig.ScenarioDefinition> items;
    private Instrument aaplInstrument;

    @BeforeEach
    void setUp() {
        items = new HashMap<>();
        ScenarioConfig.ScenarioDefinition rise5 = new ScenarioConfig.ScenarioDefinition();
        rise5.setSymbol("AAPL");
        rise5.setDirectionPct(new BigDecimal("5"));
        rise5.setTicks(10);
        items.put("SCN-AAPL-RISE-5PCT", rise5);

        ScenarioConfig.ScenarioDefinition drop5 = new ScenarioConfig.ScenarioDefinition();
        drop5.setSymbol("AAPL");
        drop5.setDirectionPct(new BigDecimal("-5"));
        drop5.setTicks(10);
        items.put("SCN-AAPL-DROP-5PCT", drop5);

        ScenarioConfig.ScenarioDefinition tslaRise = new ScenarioConfig.ScenarioDefinition();
        tslaRise.setSymbol("TSLA");
        tslaRise.setDirectionPct(new BigDecimal("5"));
        tslaRise.setTicks(10);
        items.put("SCN-TSLA-RISE-5PCT", tslaRise);

        aaplInstrument = Instrument.builder()
                .symbol("AAPL")
                .lastPrice(new BigDecimal("200.00"))
                .bidPrice(new BigDecimal("199.90"))
                .askPrice(new BigDecimal("200.10"))
                .tradable(true)
                .tradingHalted(false)
                .minQuantity(1)
                .maxQuantity(10000)
                .dataTimestamp(Instant.now())
                .build();
    }

    // --- startScenario: happy path ---

    @Test
    void shouldReturnScenarioStartedOnSuccess() {
        when(scenarioConfig.getItems()).thenReturn(items);
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(aaplInstrument));

        ScenarioRequest req = new ScenarioRequest();
        req.setScenarioId("SCN-AAPL-RISE-5PCT");
        req.setSymbol("AAPL");

        ScenarioResponse response = scenarioService.startScenario(req);

        assertThat(response.getStatus()).isEqualTo("SCENARIO_STARTED");
        assertThat(response.getScenarioId()).isEqualTo("SCN-AAPL-RISE-5PCT");
        assertThat(response.getSymbol()).isEqualTo("AAPL");
        assertThat(response.getExpectedDuration()).isEqualTo("10s");
    }

    @Test
    void shouldNormaliseSymbolToUppercase() {
        when(scenarioConfig.getItems()).thenReturn(items);
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(aaplInstrument));

        ScenarioRequest req = new ScenarioRequest();
        req.setScenarioId("SCN-AAPL-RISE-5PCT");
        req.setSymbol("aapl");

        ScenarioResponse response = scenarioService.startScenario(req);

        assertThat(response.getSymbol()).isEqualTo("AAPL");
    }

    // --- startScenario: validation errors ---

    @Test
    void shouldThrowScenarioNotFoundWhenScenarioIdUnknown() {
        when(scenarioConfig.getItems()).thenReturn(items);

        ScenarioRequest req = new ScenarioRequest();
        req.setScenarioId("SCN-UNKNOWN");
        req.setSymbol("AAPL");

        assertThatThrownBy(() -> scenarioService.startScenario(req))
                .isInstanceOf(ScenarioNotFoundException.class)
                .hasMessageContaining("SCN-UNKNOWN");
    }

    @Test
    void shouldThrowSymbolNotFoundWhenSymbolUnknown() {
        when(scenarioConfig.getItems()).thenReturn(items);
        when(instrumentRepository.findById("ZZZZ")).thenReturn(Optional.empty());

        ScenarioRequest req = new ScenarioRequest();
        req.setScenarioId("SCN-AAPL-RISE-5PCT");
        req.setSymbol("ZZZZ");

        assertThatThrownBy(() -> scenarioService.startScenario(req))
                .isInstanceOf(SymbolNotFoundException.class)
                .hasMessageContaining("ZZZZ");
    }

    @Test
    void shouldThrowSymbolHaltedWhenSymbolIsHalted() {
        when(scenarioConfig.getItems()).thenReturn(items);
        Instrument halted = Instrument.builder()
                .symbol("HALT")
                .lastPrice(new BigDecimal("50.00"))
                .tradingHalted(true)
                .tradable(true)
                .minQuantity(1).maxQuantity(10000)
                .dataTimestamp(Instant.now()).build();
        when(instrumentRepository.findById("HALT")).thenReturn(Optional.of(halted));

        ScenarioConfig.ScenarioDefinition haltDef = new ScenarioConfig.ScenarioDefinition();
        haltDef.setSymbol("HALT");
        haltDef.setDirectionPct(new BigDecimal("5"));
        haltDef.setTicks(10);
        items.put("SCN-HALT-RISE", haltDef);

        ScenarioRequest req = new ScenarioRequest();
        req.setScenarioId("SCN-HALT-RISE");
        req.setSymbol("HALT");

        assertThatThrownBy(() -> scenarioService.startScenario(req))
                .isInstanceOf(SymbolHaltedException.class)
                .hasMessageContaining("HALT");
    }

    // --- scenario progression ---

    @Test
    void shouldComputePositiveIncrementForRiseScenario() {
        when(scenarioConfig.getItems()).thenReturn(items);
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(aaplInstrument));

        ScenarioRequest req = new ScenarioRequest();
        req.setScenarioId("SCN-AAPL-RISE-5PCT");
        req.setSymbol("AAPL");
        scenarioService.startScenario(req);

        BigDecimal increment = scenarioService.applyScenarioTick("AAPL");

        assertThat(increment).isNotNull();
        assertThat(increment).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void shouldComputeNegativeIncrementForDropScenario() {
        when(scenarioConfig.getItems()).thenReturn(items);
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(aaplInstrument));

        ScenarioRequest req = new ScenarioRequest();
        req.setScenarioId("SCN-AAPL-DROP-5PCT");
        req.setSymbol("AAPL");
        scenarioService.startScenario(req);

        BigDecimal increment = scenarioService.applyScenarioTick("AAPL");

        assertThat(increment).isNotNull();
        assertThat(increment).isLessThan(BigDecimal.ZERO);
    }

    @Test
    void shouldDecrementRemainingTicksOnEachApply() {
        when(scenarioConfig.getItems()).thenReturn(items);
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(aaplInstrument));

        ScenarioRequest req = new ScenarioRequest();
        req.setScenarioId("SCN-AAPL-RISE-5PCT");
        req.setSymbol("AAPL");
        scenarioService.startScenario(req);

        assertThat(scenarioService.hasActiveScenario("AAPL")).isTrue();

        // Apply 9 ticks — scenario still active
        for (int i = 0; i < 9; i++) {
            BigDecimal increment = scenarioService.applyScenarioTick("AAPL");
            assertThat(increment).isNotNull();
        }
        assertThat(scenarioService.hasActiveScenario("AAPL")).isTrue();

        // 10th tick — scenario completes
        BigDecimal finalIncrement = scenarioService.applyScenarioTick("AAPL");
        assertThat(finalIncrement).isNotNull();
        assertThat(scenarioService.hasActiveScenario("AAPL")).isFalse();
    }

    @Test
    void shouldReturnNullWhenNoActiveScenario() {
        BigDecimal increment = scenarioService.applyScenarioTick("AAPL");

        assertThat(increment).isNull();
    }

    // --- scenario replacement ---

    @Test
    void shouldReplaceExistingScenarioForSameSymbol() {
        when(scenarioConfig.getItems()).thenReturn(items);
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(aaplInstrument));

        ScenarioRequest rise = new ScenarioRequest();
        rise.setScenarioId("SCN-AAPL-RISE-5PCT");
        rise.setSymbol("AAPL");
        scenarioService.startScenario(rise);

        // Apply a few ticks of the rise scenario
        scenarioService.applyScenarioTick("AAPL");
        scenarioService.applyScenarioTick("AAPL");

        // Start a drop scenario — should replace the rise
        ScenarioRequest drop = new ScenarioRequest();
        drop.setScenarioId("SCN-AAPL-DROP-5PCT");
        drop.setSymbol("AAPL");
        scenarioService.startScenario(drop);

        BigDecimal increment = scenarioService.applyScenarioTick("AAPL");
        assertThat(increment).isLessThan(BigDecimal.ZERO); // drop scenario active
    }

    // --- concurrent scenarios for different symbols ---

    @Test
    void shouldSupportConcurrentScenariosForDifferentSymbols() {
        when(scenarioConfig.getItems()).thenReturn(items);
        Instrument tsla = Instrument.builder()
                .symbol("TSLA")
                .lastPrice(new BigDecimal("300.00"))
                .bidPrice(new BigDecimal("299.90"))
                .askPrice(new BigDecimal("300.10"))
                .tradable(true)
                .tradingHalted(false)
                .minQuantity(1).maxQuantity(10000)
                .dataTimestamp(Instant.now()).build();
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(aaplInstrument));
        when(instrumentRepository.findById("TSLA")).thenReturn(Optional.of(tsla));

        ScenarioRequest aaplReq = new ScenarioRequest();
        aaplReq.setScenarioId("SCN-AAPL-RISE-5PCT");
        aaplReq.setSymbol("AAPL");
        scenarioService.startScenario(aaplReq);

        ScenarioRequest tslaReq = new ScenarioRequest();
        tslaReq.setScenarioId("SCN-TSLA-RISE-5PCT");
        tslaReq.setSymbol("TSLA");
        scenarioService.startScenario(tslaReq);

        assertThat(scenarioService.hasActiveScenario("AAPL")).isTrue();
        assertThat(scenarioService.hasActiveScenario("TSLA")).isTrue();

        BigDecimal aaplIncrement = scenarioService.applyScenarioTick("AAPL");
        BigDecimal tslaIncrement = scenarioService.applyScenarioTick("TSLA");

        assertThat(aaplIncrement).isGreaterThan(BigDecimal.ZERO);
        assertThat(tslaIncrement).isGreaterThan(BigDecimal.ZERO);
    }

    // --- hasActiveScenario ---

    @Test
    void shouldReturnFalseWhenNoScenarioStarted() {
        assertThat(scenarioService.hasActiveScenario("AAPL")).isFalse();
    }

    @Test
    void shouldReturnTrueAfterScenarioStarted() {
        when(scenarioConfig.getItems()).thenReturn(items);
        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(aaplInstrument));

        ScenarioRequest req = new ScenarioRequest();
        req.setScenarioId("SCN-AAPL-RISE-5PCT");
        req.setSymbol("AAPL");
        scenarioService.startScenario(req);

        assertThat(scenarioService.hasActiveScenario("AAPL")).isTrue();
    }
}
