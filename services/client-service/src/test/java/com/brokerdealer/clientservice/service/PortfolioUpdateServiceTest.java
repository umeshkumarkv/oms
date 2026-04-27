package com.brokerdealer.clientservice.service;

import com.brokerdealer.clientservice.domain.*;
import com.brokerdealer.clientservice.kafka.event.ExecutionCompletedEvent;
import com.brokerdealer.clientservice.kafka.event.OrderStateChangedEvent;
import com.brokerdealer.clientservice.repository.ClientRepository;
import com.brokerdealer.clientservice.repository.HoldingRepository;
import com.brokerdealer.clientservice.repository.ProcessedFillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioUpdateServiceTest {

    @Mock ClientRepository clientRepository;
    @Mock HoldingRepository holdingRepository;
    @Mock ProcessedFillRepository processedFillRepository;
    @Mock RedisTemplate<String, Object> redisTemplate;

    @InjectMocks PortfolioUpdateService service;

    private static final UUID CLIENT_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID   = UUID.randomUUID();
    private static final UUID EXEC_ID    = UUID.randomUUID();

    private Client buildClient(BigDecimal cashBalance, BigDecimal reservedCash) {
        return Client.builder()
                .id(CLIENT_ID)
                .name("John Smith")
                .accountType(AccountType.CASH)
                .status(ClientStatus.ACTIVE)
                .tradingEnabled(true)
                .kycStatus(KycStatus.VERIFIED)
                .kycExpiryDate(LocalDate.of(2027, 12, 31))
                .marginCallActive(false)
                .cashBalance(cashBalance)
                .reservedCash(reservedCash)
                .dailyOrderCount(0)
                .dailyOrderValue(BigDecimal.ZERO)
                .build();
    }

    private Holding buildHolding(String symbol, int qty, String avgPrice) {
        return Holding.builder()
                .id(UUID.randomUUID())
                .clientId(CLIENT_ID)
                .symbol(symbol)
                .quantity(qty)
                .avgPrice(new BigDecimal(avgPrice))
                .build();
    }

    // ─── order.state.changed consumer tests ────────────────────────────────────

    @Test
    void shouldReleaseReservedCashOnBuyFilled() {
        Client client = buildClient(new BigDecimal("500000.00"), new BigDecimal("17545.00"));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(clientRepository.save(any())).thenReturn(client);

        OrderStateChangedEvent event = OrderStateChangedEvent.builder()
                .orderId(ORDER_ID).clientId(CLIENT_ID).side("BUY")
                .newState("FILLED").previousState("SENT_TO_EXECUTION")
                .orderValue("17545.00").timestamp(Instant.now()).build();

        service.handleOrderStateChanged(event);

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        assertThat(captor.getValue().getReservedCash()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(redisTemplate).delete("client:portfolio:" + CLIENT_ID);
        verify(redisTemplate).delete("client:balance:" + CLIENT_ID);
    }

    @Test
    void shouldReleaseReservedCashOnBuyRiskRejected() {
        Client client = buildClient(new BigDecimal("100000.00"), new BigDecimal("5000.00"));
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(clientRepository.save(any())).thenReturn(client);

        OrderStateChangedEvent event = OrderStateChangedEvent.builder()
                .orderId(ORDER_ID).clientId(CLIENT_ID).side("BUY")
                .newState("RISK_REJECTED").previousState("VALIDATED")
                .orderValue("5000.00").timestamp(Instant.now()).build();

        service.handleOrderStateChanged(event);

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        assertThat(captor.getValue().getReservedCash()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldNotChangeReservedCashForSellOrder() {
        OrderStateChangedEvent event = OrderStateChangedEvent.builder()
                .orderId(ORDER_ID).clientId(CLIENT_ID).side("SELL")
                .newState("FILLED").previousState("SENT_TO_EXECUTION")
                .orderValue("5000.00").timestamp(Instant.now()).build();

        service.handleOrderStateChanged(event);

        verify(clientRepository, never()).findById(any());
        verify(clientRepository, never()).save(any());
    }

    @Test
    void shouldSkipNonTerminalStateForBuyOrder() {
        OrderStateChangedEvent event = OrderStateChangedEvent.builder()
                .orderId(ORDER_ID).clientId(CLIENT_ID).side("BUY")
                .newState("VALIDATED").previousState("NEW")
                .orderValue("5000.00").timestamp(Instant.now()).build();

        service.handleOrderStateChanged(event);

        verify(clientRepository, never()).findById(any());
    }

    @Test
    void shouldHandleIdempotentStateChangedWhenReservedAlreadyReleased() {
        Client client = buildClient(new BigDecimal("100000.00"), BigDecimal.ZERO);
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));

        OrderStateChangedEvent event = OrderStateChangedEvent.builder()
                .orderId(ORDER_ID).clientId(CLIENT_ID).side("BUY")
                .newState("FILLED").previousState("SENT_TO_EXECUTION")
                .orderValue("5000.00").timestamp(Instant.now()).build();

        service.handleOrderStateChanged(event);

        // Should not save — already released (idempotent)
        verify(clientRepository, never()).save(any());
    }

    // ─── execution.completed consumer tests ────────────────────────────────────

    @Test
    void shouldUpdateCashAndHoldingsOnBuyFill() {
        Client client = buildClient(new BigDecimal("500000.00"), new BigDecimal("17545.00"));
        Holding existing = buildHolding("AAPL", 500, "168.4000");

        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(holdingRepository.findByClientIdAndSymbol(CLIENT_ID, "AAPL")).thenReturn(Optional.of(existing));
        when(processedFillRepository.existsByExecutionId(EXEC_ID)).thenReturn(false);
        when(clientRepository.save(any())).thenReturn(client);

        ExecutionCompletedEvent event = ExecutionCompletedEvent.builder()
                .executionId(EXEC_ID).orderId(ORDER_ID).clientId(CLIENT_ID)
                .symbol("AAPL").side("BUY").fillQuantity(100).fillPrice("175.45")
                .fillType("FULL").executedAt(Instant.now()).build();

        service.handleExecutionCompleted(event);

        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(clientCaptor.capture());
        // cash_balance reduced by 100 × 175.45 = 17545.00
        assertThat(clientCaptor.getValue().getCashBalance())
                .isEqualByComparingTo(new BigDecimal("482455.00"));

        ArgumentCaptor<Holding> holdingCaptor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(holdingCaptor.capture());
        // quantity: 500 + 100 = 600
        assertThat(holdingCaptor.getValue().getQuantity()).isEqualTo(600);
        // avgPrice: (500×168.40 + 100×175.45) / 600 = (84200 + 17545) / 600 = 169.5750
        assertThat(holdingCaptor.getValue().getAvgPrice())
                .isEqualByComparingTo(new BigDecimal("169.5750"));

        verify(processedFillRepository).save(any(ProcessedFill.class));
        verify(redisTemplate).delete("client:portfolio:" + CLIENT_ID);
        verify(redisTemplate).delete("client:balance:" + CLIENT_ID);
    }

    @Test
    void shouldCreateNewHoldingOnBuyFillWithNoExistingHolding() {
        Client client = buildClient(new BigDecimal("50000.00"), BigDecimal.ZERO);

        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(holdingRepository.findByClientIdAndSymbol(CLIENT_ID, "NVDA")).thenReturn(Optional.empty());
        when(processedFillRepository.existsByExecutionId(EXEC_ID)).thenReturn(false);
        when(clientRepository.save(any())).thenReturn(client);

        ExecutionCompletedEvent event = ExecutionCompletedEvent.builder()
                .executionId(EXEC_ID).orderId(ORDER_ID).clientId(CLIENT_ID)
                .symbol("NVDA").side("BUY").fillQuantity(10).fillPrice("870.00")
                .fillType("FULL").executedAt(Instant.now()).build();

        service.handleExecutionCompleted(event);

        ArgumentCaptor<Holding> captor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(captor.capture());
        assertThat(captor.getValue().getSymbol()).isEqualTo("NVDA");
        assertThat(captor.getValue().getQuantity()).isEqualTo(10);
        assertThat(captor.getValue().getAvgPrice()).isEqualByComparingTo(new BigDecimal("870.0000"));
    }

    @Test
    void shouldUpdateCashAndReduceHoldingsOnSellFill() {
        Client client = buildClient(new BigDecimal("50000.00"), BigDecimal.ZERO);
        Holding existing = buildHolding("TSLA", 20, "170.0000");

        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(holdingRepository.findByClientIdAndSymbol(CLIENT_ID, "TSLA")).thenReturn(Optional.of(existing));
        when(processedFillRepository.existsByExecutionId(EXEC_ID)).thenReturn(false);
        when(clientRepository.save(any())).thenReturn(client);

        ExecutionCompletedEvent event = ExecutionCompletedEvent.builder()
                .executionId(EXEC_ID).orderId(ORDER_ID).clientId(CLIENT_ID)
                .symbol("TSLA").side("SELL").fillQuantity(5).fillPrice("185.00")
                .fillType("FULL").executedAt(Instant.now()).build();

        service.handleExecutionCompleted(event);

        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(clientCaptor.capture());
        // cash_balance increased by 5 × 185.00 = 925.00
        assertThat(clientCaptor.getValue().getCashBalance())
                .isEqualByComparingTo(new BigDecimal("50925.00"));

        ArgumentCaptor<Holding> holdingCaptor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(holdingCaptor.capture());
        assertThat(holdingCaptor.getValue().getQuantity()).isEqualTo(15);
    }

    @Test
    void shouldDeleteHoldingWhenSellReducesQuantityToZero() {
        Client client = buildClient(new BigDecimal("50000.00"), BigDecimal.ZERO);
        Holding existing = buildHolding("TSLA", 20, "170.0000");

        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(holdingRepository.findByClientIdAndSymbol(CLIENT_ID, "TSLA")).thenReturn(Optional.of(existing));
        when(processedFillRepository.existsByExecutionId(EXEC_ID)).thenReturn(false);
        when(clientRepository.save(any())).thenReturn(client);

        ExecutionCompletedEvent event = ExecutionCompletedEvent.builder()
                .executionId(EXEC_ID).orderId(ORDER_ID).clientId(CLIENT_ID)
                .symbol("TSLA").side("SELL").fillQuantity(20).fillPrice("185.00")
                .fillType("FULL").executedAt(Instant.now()).build();

        service.handleExecutionCompleted(event);

        verify(holdingRepository).delete(existing);
        verify(holdingRepository, never()).save(any());
    }

    @Test
    void shouldSkipDuplicateExecutionCompletedEvent() {
        when(processedFillRepository.existsByExecutionId(EXEC_ID)).thenReturn(true);

        ExecutionCompletedEvent event = ExecutionCompletedEvent.builder()
                .executionId(EXEC_ID).orderId(ORDER_ID).clientId(CLIENT_ID)
                .symbol("AAPL").side("BUY").fillQuantity(100).fillPrice("175.45")
                .fillType("FULL").executedAt(Instant.now()).build();

        service.handleExecutionCompleted(event);

        verify(clientRepository, never()).findById(any());
        verify(clientRepository, never()).save(any());
    }

    @Test
    void shouldSkipRejectedFillType() {
        ExecutionCompletedEvent event = ExecutionCompletedEvent.builder()
                .executionId(EXEC_ID).orderId(ORDER_ID).clientId(CLIENT_ID)
                .symbol("AAPL").side("BUY").fillQuantity(100).fillPrice("175.45")
                .fillType("REJECTED").executedAt(Instant.now()).build();

        service.handleExecutionCompleted(event);

        verify(processedFillRepository, never()).existsByExecutionId(any());
        verify(clientRepository, never()).findById(any());
    }

    @Test
    void shouldCalculateAvgPriceCorrectlyForVolWeightedAverage() {
        // CLT-001 scenario from AC: 500 AAPL @ 168.40, fill 100 @ 175.45
        // new_avg = (500×168.40 + 100×175.45) / 600 = (84200 + 17545) / 600 = 101745/600 = 169.575
        Client client = buildClient(new BigDecimal("500000.00"), BigDecimal.ZERO);
        Holding existing = buildHolding("AAPL", 500, "168.4000");

        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(holdingRepository.findByClientIdAndSymbol(CLIENT_ID, "AAPL")).thenReturn(Optional.of(existing));
        when(processedFillRepository.existsByExecutionId(EXEC_ID)).thenReturn(false);
        when(clientRepository.save(any())).thenReturn(client);

        ExecutionCompletedEvent event = ExecutionCompletedEvent.builder()
                .executionId(EXEC_ID).orderId(ORDER_ID).clientId(CLIENT_ID)
                .symbol("AAPL").side("BUY").fillQuantity(100).fillPrice("175.45")
                .fillType("FULL").executedAt(Instant.now()).build();

        service.handleExecutionCompleted(event);

        ArgumentCaptor<Holding> captor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(captor.capture());
        assertThat(captor.getValue().getAvgPrice())
                .isEqualByComparingTo(new BigDecimal("169.5750"));
        assertThat(captor.getValue().getQuantity()).isEqualTo(600);
    }
}
