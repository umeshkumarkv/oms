package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.repository.TickRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TickPersistenceServiceTest {

    @Mock
    private TickRepository tickRepository;

    @InjectMocks
    private TickPersistenceService tickPersistenceService;

    @Test
    void shouldDelegateToRepositoryWithCorrectArgs() {
        BigDecimal price  = new BigDecimal("175.0000");
        BigDecimal bid    = new BigDecimal("174.9000");
        BigDecimal ask    = new BigDecimal("175.1000");
        Long       volume = 1000L;

        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        tickPersistenceService.saveTick("AAPL", price, bid, ask, volume);
        Instant after  = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        ArgumentCaptor<Instant> timeCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(tickRepository).upsertTick(eq("AAPL"), eq(price), eq(bid), eq(ask), eq(volume),
                timeCaptor.capture());

        Instant captured = timeCaptor.getValue();
        // tickTime must be truncated to second
        assertThat(captured).isEqualTo(captured.truncatedTo(ChronoUnit.SECONDS));
        // tickTime must fall within the test window
        assertThat(captured).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
    }

    @Test
    void shouldNotPropagateExceptionWhenRepositoryFails() {
        doThrow(new RuntimeException("DB unavailable"))
                .when(tickRepository)
                .upsertTick(any(), any(), any(), any(), any(), any());

        assertThatCode(() -> tickPersistenceService.saveTick(
                "TSLA",
                new BigDecimal("250.0000"),
                new BigDecimal("249.9000"),
                new BigDecimal("250.1000"),
                500L))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHandleNullBidAndAsk() {
        assertThatCode(() -> tickPersistenceService.saveTick("MSFT",
                new BigDecimal("310.0000"), null, null, 200L))
                .doesNotThrowAnyException();

        verify(tickRepository).upsertTick(eq("MSFT"), eq(new BigDecimal("310.0000")),
                eq(null), eq(null), eq(200L), any(Instant.class));
    }
}
