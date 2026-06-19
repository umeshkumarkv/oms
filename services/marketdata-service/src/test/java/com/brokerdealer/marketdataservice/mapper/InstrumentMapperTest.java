package com.brokerdealer.marketdataservice.mapper;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.dto.response.InstrumentResponse;
import com.brokerdealer.marketdataservice.dto.response.InstrumentSummaryResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentMapperTest {

    private final InstrumentMapper instrumentMapper = Mappers.getMapper(InstrumentMapper.class);
    private final InstrumentSummaryMapper summaryMapper = Mappers.getMapper(InstrumentSummaryMapper.class);

    @Test
    void shouldMapCusipAndSecNumberToInstrumentResponse() {
        Instrument instrument = buildInstrument("AAPL", "037833100", "320193");

        InstrumentResponse response = instrumentMapper.toResponse(instrument);

        assertThat(response.getCusip()).isEqualTo("037833100");
        assertThat(response.getSecNumber()).isEqualTo("320193");
        assertThat(response.getSymbol()).isEqualTo("AAPL");
    }

    @Test
    void shouldReturnNullCusipAndSecNumberWhenEntityFieldsAreNull() {
        Instrument instrument = buildInstrument("MSFT", null, null);

        InstrumentResponse response = instrumentMapper.toResponse(instrument);

        assertThat(response.getCusip()).isNull();
        assertThat(response.getSecNumber()).isNull();
    }

    @Test
    void shouldMapCusipAndSecNumberToInstrumentSummaryResponse() {
        Instrument instrument = buildInstrument("AAPL", "037833100", "320193");

        InstrumentSummaryResponse response = summaryMapper.toSummary(instrument);

        assertThat(response.getCusip()).isEqualTo("037833100");
        assertThat(response.getSecNumber()).isEqualTo("320193");
        assertThat(response.getSymbol()).isEqualTo("AAPL");
    }

    @Test
    void shouldReturnNullCusipAndSecNumberInSummaryWhenEntityFieldsAreNull() {
        Instrument instrument = buildInstrument("HALT", null, null);

        InstrumentSummaryResponse response = summaryMapper.toSummary(instrument);

        assertThat(response.getCusip()).isNull();
        assertThat(response.getSecNumber()).isNull();
    }

    private Instrument buildInstrument(String symbol, String cusip, String secNumber) {
        return Instrument.builder()
                .symbol(symbol)
                .bidPrice(new BigDecimal("174.95"))
                .askPrice(new BigDecimal("175.05"))
                .lastPrice(new BigDecimal("175.00"))
                .tradable(true)
                .tradingHalted(false)
                .minQuantity(1)
                .maxQuantity(10000)
                .dataTimestamp(Instant.now())
                .cusip(cusip)
                .secNumber(secNumber)
                .build();
    }
}
