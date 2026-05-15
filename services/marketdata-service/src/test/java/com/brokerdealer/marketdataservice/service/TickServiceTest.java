package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.domain.Tick;
import com.brokerdealer.marketdataservice.dto.response.TickHistoryResponse;
import com.brokerdealer.marketdataservice.repository.TickRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TickServiceTest {

    @Mock
    private TickRepository tickRepository;

    @InjectMocks
    private TickService tickService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tickService, "timezone", "America/New_York");
    }

    @Test
    void shouldReturnTickHistoryWithCorrectSymbolAndRange() {
        Tick tick = buildTick("AAPL", new BigDecimal("175.00"), new BigDecimal("174.90"),
                new BigDecimal("175.10"), 1000L);
        when(tickRepository.findHistory(eq("AAPL"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(tick));

        TickHistoryResponse result = tickService.getHistory("AAPL");

        assertThat(result.getSymbol()).isEqualTo("AAPL");
        assertThat(result.getTicks()).hasSize(1);
        assertThat(result.getFrom()).isNotNull();
        assertThat(result.getTo()).isNotNull();
    }

    @Test
    void shouldUppercaseSymbolBeforeQuerying() {
        when(tickRepository.findHistory(eq("AAPL"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        tickService.getHistory("aapl");

        verify(tickRepository).findHistory(eq("AAPL"), any(Instant.class), any(Instant.class));
    }

    @Test
    void shouldReturnEmptyTickListWhenNoTicksFound() {
        when(tickRepository.findHistory(eq("TSLA"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        TickHistoryResponse result = tickService.getHistory("TSLA");

        assertThat(result.getSymbol()).isEqualTo("TSLA");
        assertThat(result.getTicks()).isEmpty();
    }

    @Test
    void shouldMapTickFieldsAsStrings() {
        Tick tick = buildTick("MSFT", new BigDecimal("310.50"), new BigDecimal("310.40"),
                new BigDecimal("310.60"), 500L);
        when(tickRepository.findHistory(eq("MSFT"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(tick));

        TickHistoryResponse result = tickService.getHistory("MSFT");

        assertThat(result.getTicks()).hasSize(1);
        assertThat(result.getTicks().get(0).getPrice()).isEqualTo("310.5000");
        assertThat(result.getTicks().get(0).getBid()).isEqualTo("310.4000");
        assertThat(result.getTicks().get(0).getAsk()).isEqualTo("310.6000");
        assertThat(result.getTicks().get(0).getVolume()).isEqualTo(500L);
    }

    @Test
    void shouldFormatTimeAsEtLocalHhMm() {
        // A tick at 14:31 UTC = 10:31 ET (UTC-4 in summer)
        Instant tickInstant = ZonedDateTime.of(2026, 5, 8, 14, 31, 0, 0,
                ZoneId.of("UTC")).toInstant();
        Tick tick = buildTick("AAPL", new BigDecimal("175.00"), null, null, null);
        tick.setTickTime(tickInstant);

        when(tickRepository.findHistory(eq("AAPL"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(tick));

        TickHistoryResponse result = tickService.getHistory("AAPL");

        // 14:31 UTC = 10:31 ET (America/New_York, EDT = UTC-4)
        assertThat(result.getTicks().get(0).getTime()).isEqualTo("10:31");
    }

    @Test
    void shouldQueryFromMidnightEtToCurrentMinute() {
        when(tickRepository.findHistory(eq("AAPL"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        tickService.getHistory("AAPL");

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor   = ArgumentCaptor.forClass(Instant.class);
        verify(tickRepository).findHistory(eq("AAPL"), fromCaptor.capture(), toCaptor.capture());

        Instant from = fromCaptor.getValue();
        Instant to   = toCaptor.getValue();

        // 'to' must be truncated to minute and from must be before to
        assertThat(to).isEqualTo(to.truncatedTo(ChronoUnit.MINUTES));
        assertThat(from).isBefore(to);

        // 'from' must equal today midnight ET as a UTC Instant
        ZoneId  et           = ZoneId.of("America/New_York");
        Instant expectedFrom = ZonedDateTime.now(et).toLocalDate().atStartOfDay(et).toInstant();
        assertThat(from).isEqualTo(expectedFrom);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Tick buildTick(String symbol, BigDecimal price, BigDecimal bid,
                           BigDecimal ask, Long volume) {
        Tick tick = new Tick();
        tick.setId(1L);
        tick.setSymbol(symbol);
        tick.setPrice(price);
        tick.setBid(bid);
        tick.setAsk(ask);
        tick.setVolume(volume);
        tick.setTickTime(Instant.now());
        return tick;
    }
}
