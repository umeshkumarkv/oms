package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.dto.response.InstrumentResponse;
import com.brokerdealer.marketdataservice.exception.SymbolNotFoundException;
import com.brokerdealer.marketdataservice.mapper.InstrumentMapper;
import com.brokerdealer.marketdataservice.repository.InstrumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private InstrumentMapper instrumentMapper;

    @InjectMocks
    private MarketDataService marketDataService;

    @Test
    void getInstrument_validSymbol_returnsResponse() {
        Instrument instrument = buildInstrument("AAPL", false);
        InstrumentResponse expected = buildResponse("AAPL", false);

        when(instrumentRepository.findById("AAPL")).thenReturn(Optional.of(instrument));
        when(instrumentMapper.toResponse(instrument)).thenReturn(expected);

        InstrumentResponse result = marketDataService.getInstrument("AAPL");

        assertThat(result).isNotNull();
        assertThat(result.getSymbol()).isEqualTo("AAPL");
        assertThat(result.getHalted()).isFalse();
        assertThat(result.getTradable()).isTrue();
    }

    @Test
    void getInstrument_haltedSymbol_returnsHaltedTrue() {
        Instrument instrument = buildInstrument("HALT", true);
        InstrumentResponse expected = buildResponse("HALT", true);

        when(instrumentRepository.findById("HALT")).thenReturn(Optional.of(instrument));
        when(instrumentMapper.toResponse(instrument)).thenReturn(expected);

        InstrumentResponse result = marketDataService.getInstrument("HALT");

        assertThat(result).isNotNull();
        assertThat(result.getSymbol()).isEqualTo("HALT");
        assertThat(result.getHalted()).isTrue();
        assertThat(result.getTradable()).isTrue();
    }

    @Test
    void getInstrument_unknownSymbol_throwsSymbolNotFoundException() {
        when(instrumentRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marketDataService.getInstrument("UNKNOWN"))
                .isInstanceOf(SymbolNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
    }

    private Instrument buildInstrument(String symbol, boolean halted) {
        return Instrument.builder()
                .symbol(symbol)
                .bidPrice(new BigDecimal("174.95"))
                .askPrice(new BigDecimal("175.05"))
                .lastPrice(new BigDecimal("175.00"))
                .tradable(true)
                .tradingHalted(halted)
                .minQuantity(1)
                .maxQuantity(10000)
                .dataTimestamp(Instant.now())
                .build();
    }

    private InstrumentResponse buildResponse(String symbol, boolean halted) {
        return InstrumentResponse.builder()
                .symbol(symbol)
                .bidPrice("174.95")
                .askPrice("175.05")
                .lastPrice("175.00")
                .tradable(true)
                .halted(halted)
                .minQuantity(1)
                .maxQuantity(10000)
                .dataTimestamp(Instant.now().toString())
                .build();
    }
}
