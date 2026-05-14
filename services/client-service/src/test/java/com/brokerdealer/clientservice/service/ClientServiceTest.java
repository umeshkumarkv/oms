package com.brokerdealer.clientservice.service;

import com.brokerdealer.clientservice.client.MarketDataClient;
import com.brokerdealer.clientservice.domain.*;
import com.brokerdealer.clientservice.dto.request.ReservedCashRequest;
import com.brokerdealer.clientservice.dto.response.ClientProfileResponse;
import com.brokerdealer.clientservice.dto.response.PortfolioResponse;
import com.brokerdealer.clientservice.dto.response.ReservedCashResponse;
import com.brokerdealer.clientservice.exception.ClientNotFoundException;
import com.brokerdealer.clientservice.exception.InvalidReservationException;
import com.brokerdealer.clientservice.mapper.ClientMapper;
import com.brokerdealer.clientservice.repository.ClientRepository;
import com.brokerdealer.clientservice.repository.HoldingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    ClientRepository clientRepository;

    @Mock
    HoldingRepository holdingRepository;

    @Mock
    ClientMapper clientMapper;

    @Mock
    MarketDataClient marketDataClient;

    @InjectMocks
    ClientService clientService;

    private Client buildClient(UUID id, String name, ClientStatus status,
                                boolean tradingEnabled, KycStatus kycStatus,
                                LocalDate kycExpiry) {
        return Client.builder()
                .id(id)
                .name(name)
                .accountType(AccountType.CASH)
                .status(status)
                .tradingEnabled(tradingEnabled)
                .kycStatus(kycStatus)
                .kycExpiryDate(kycExpiry)
                .marginCallActive(false)
                .cashBalance(BigDecimal.valueOf(100000))
                .reservedCash(BigDecimal.ZERO)
                .dailyOrderCount(0)
                .dailyOrderValue(BigDecimal.ZERO)
                .build();
    }

    @Test
    void shouldReturnClientProfileForValidClient() {
        UUID id = UUID.fromString("a1000000-0000-0000-0000-000000000001");
        Client client = buildClient(id, "John Smith", ClientStatus.ACTIVE, true,
                KycStatus.VERIFIED, LocalDate.of(2027, 12, 31));
        ClientProfileResponse expected = ClientProfileResponse.builder()
                .clientId(id).name("John Smith").accountType(AccountType.CASH)
                .status(ClientStatus.ACTIVE).tradingEnabled(true)
                .kycStatus(KycStatus.VERIFIED).kycExpiryDate(LocalDate.of(2027, 12, 31))
                .build();

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));
        when(clientMapper.toProfileResponse(client)).thenReturn(expected);

        ClientProfileResponse result = clientService.getClient(id);

        assertThat(result).isEqualTo(expected);
        verify(clientRepository).findById(id);
        verify(clientMapper).toProfileResponse(client);
    }

    @Test
    void shouldThrowClientNotFoundExceptionForUnknownId() {
        UUID id = UUID.randomUUID();
        when(clientRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.getClient(id))
                .isInstanceOf(ClientNotFoundException.class)
                .hasMessageContaining(id.toString());

        ClientNotFoundException ex = catchThrowableOfType(
                () -> clientService.getClient(id), ClientNotFoundException.class);
        assertThat(ex.getErrorCode()).isEqualTo("CLIENT_NOT_FOUND");
    }

    @Test
    void shouldReturnTradingDisabledForClient003() {
        UUID id = UUID.fromString("a3000000-0000-0000-0000-000000000003");
        Client client = buildClient(id, "Mike Davis", ClientStatus.ACTIVE, false,
                KycStatus.VERIFIED, LocalDate.of(2027, 12, 31));
        ClientProfileResponse expected = ClientProfileResponse.builder()
                .clientId(id).name("Mike Davis").accountType(AccountType.CASH)
                .status(ClientStatus.ACTIVE).tradingEnabled(false)
                .kycStatus(KycStatus.VERIFIED).kycExpiryDate(LocalDate.of(2027, 12, 31))
                .build();

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));
        when(clientMapper.toProfileResponse(client)).thenReturn(expected);

        ClientProfileResponse result = clientService.getClient(id);

        assertThat(result.getTradingEnabled()).isFalse();
    }

    @Test
    void shouldReturnBlockedStatusForClient004() {
        UUID id = UUID.fromString("a4000000-0000-0000-0000-000000000004");
        Client client = buildClient(id, "Emily Chen", ClientStatus.BLOCKED, true,
                KycStatus.VERIFIED, LocalDate.of(2027, 12, 31));
        ClientProfileResponse expected = ClientProfileResponse.builder()
                .clientId(id).name("Emily Chen").accountType(AccountType.CASH)
                .status(ClientStatus.BLOCKED).tradingEnabled(true)
                .kycStatus(KycStatus.VERIFIED).kycExpiryDate(LocalDate.of(2027, 12, 31))
                .build();

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));
        when(clientMapper.toProfileResponse(client)).thenReturn(expected);

        ClientProfileResponse result = clientService.getClient(id);

        assertThat(result.getStatus()).isEqualTo(ClientStatus.BLOCKED);
    }

    private Client buildClientWithBalance(UUID id, BigDecimal cashBalance, BigDecimal reservedCash) {
        return Client.builder()
                .id(id)
                .name("Test Client")
                .accountType(AccountType.CASH)
                .status(ClientStatus.ACTIVE)
                .tradingEnabled(true)
                .kycStatus(KycStatus.VERIFIED)
                .kycExpiryDate(LocalDate.of(2027, 12, 31))
                .marginCallActive(false)
                .maxOpenOrders(10)
                .cashBalance(cashBalance)
                .reservedCash(reservedCash)
                .dailyOrderCount(0)
                .dailyOrderValue(BigDecimal.ZERO)
                .build();
    }

    private Holding buildHolding(UUID clientId, String symbol, int quantity, String avgPrice) {
        return Holding.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .symbol(symbol)
                .quantity(quantity)
                .avgPrice(new BigDecimal(avgPrice))
                .build();
    }

    @Test
    void shouldReturnPortfolioWithHoldingsAndComputedValues() {
        UUID id = UUID.fromString("a1000000-0000-0000-0000-000000000001");
        Client client = buildClientWithBalance(id, new BigDecimal("100000.00"), new BigDecimal("5000.00"));
        Holding aaplHolding = buildHolding(id, "AAPL", 500, "168.4000");

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));
        when(holdingRepository.findByClientId(id)).thenReturn(List.of(aaplHolding));
        when(marketDataClient.getLastPrice("AAPL")).thenReturn(new BigDecimal("185.00"));

        PortfolioResponse result = clientService.getPortfolio(id);

        assertThat(result.getClientId()).isEqualTo(id);
        assertThat(result.getName()).isEqualTo("Test Client");
        assertThat(result.getStatus()).isEqualTo(ClientStatus.ACTIVE);
        assertThat(result.getTradingEnabled()).isTrue();
        assertThat(result.getKycStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(result.getMarginCallActive()).isFalse();
        assertThat(result.getMaxOpenOrders()).isEqualTo(10);
        assertThat(result.getCashBalance()).isEqualTo("100000.00");
        assertThat(result.getReservedCash()).isEqualTo("5000.00");
        assertThat(result.getBuyingPower()).isEqualTo("95000.00");
        assertThat(result.getHoldings()).hasSize(1);
        assertThat(result.getHoldings().get(0).getSymbol()).isEqualTo("AAPL");
        assertThat(result.getHoldings().get(0).getQuantity()).isEqualTo(500);
        assertThat(result.getHoldings().get(0).getCurrentPrice()).isEqualTo("185.00");
        assertThat(result.getHoldings().get(0).getMarketValue()).isEqualTo("92500.00");
        assertThat(result.getHoldings().get(0).getUnrealisedPnL()).isEqualTo("8300.00");
    }

    @Test
    void shouldReturnEmptyHoldingsForClientWithNoHoldings() {
        UUID id = UUID.fromString("a5000000-0000-0000-0000-000000000005");
        Client client = buildClientWithBalance(id, new BigDecimal("50000.00"), BigDecimal.ZERO);

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));
        when(holdingRepository.findByClientId(id)).thenReturn(Collections.emptyList());

        PortfolioResponse result = clientService.getPortfolio(id);

        assertThat(result.getHoldings()).isEmpty();
        assertThat(result.getBuyingPower()).isEqualTo("50000.00");
    }

    @Test
    void shouldReturnNullPriceFieldsWhenMarketDataUnavailable() {
        UUID id = UUID.fromString("a1000000-0000-0000-0000-000000000001");
        Client client = buildClientWithBalance(id, new BigDecimal("100000.00"), BigDecimal.ZERO);
        Holding holding = buildHolding(id, "AAPL", 500, "168.4000");

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));
        when(holdingRepository.findByClientId(id)).thenReturn(List.of(holding));
        when(marketDataClient.getLastPrice("AAPL")).thenReturn(null);

        PortfolioResponse result = clientService.getPortfolio(id);

        assertThat(result.getHoldings()).hasSize(1);
        assertThat(result.getHoldings().get(0).getCurrentPrice()).isNull();
        assertThat(result.getHoldings().get(0).getMarketValue()).isNull();
        assertThat(result.getHoldings().get(0).getUnrealisedPnL()).isNull();
    }

    @Test
    void shouldThrowClientNotFoundForUnknownIdOnPortfolio() {
        UUID id = UUID.randomUUID();
        when(clientRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.getPortfolio(id))
                .isInstanceOf(ClientNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo("CLIENT_NOT_FOUND");
    }

    private ReservedCashRequest buildReservedCashRequest(String adjustment) {
        ReservedCashRequest req = new ReservedCashRequest();
        req.setAdjustment(adjustment);
        req.setReason("test reason");
        return req;
    }

    @Test
    void shouldIncreaseReservedCashOnPositiveAdjustment() {
        UUID id = UUID.randomUUID();
        Client client = buildClientWithBalance(id, new BigDecimal("100000.00"), new BigDecimal("5000.00"));

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenReturn(client);

        ReservedCashResponse result = clientService.adjustReservedCash(id, buildReservedCashRequest("10000.00"));

        assertThat(result.getClientId()).isEqualTo(id);
        assertThat(result.getReservedCash()).isEqualTo("15000.00");
        assertThat(result.getBuyingPower()).isEqualTo("85000.00");
    }

    @Test
    void shouldDecreaseReservedCashOnNegativeAdjustment() {
        UUID id = UUID.randomUUID();
        Client client = buildClientWithBalance(id, new BigDecimal("100000.00"), new BigDecimal("20000.00"));

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenReturn(client);

        ReservedCashResponse result = clientService.adjustReservedCash(id, buildReservedCashRequest("-5000.00"));

        assertThat(result.getReservedCash()).isEqualTo("15000.00");
        assertThat(result.getBuyingPower()).isEqualTo("85000.00");
    }

    @Test
    void shouldThrowInvalidReservationWhenResultWouldGoBelowZero() {
        UUID id = UUID.randomUUID();
        Client client = buildClientWithBalance(id, new BigDecimal("100000.00"), new BigDecimal("5000.00"));

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> clientService.adjustReservedCash(id, buildReservedCashRequest("-10000.00")))
                .isInstanceOf(InvalidReservationException.class)
                .extracting("errorCode")
                .isEqualTo("RESERVED_CASH_BELOW_ZERO");
    }

    @Test
    void shouldThrowInvalidReservationWhenResultWouldExceedCashBalance() {
        UUID id = UUID.randomUUID();
        Client client = buildClientWithBalance(id, new BigDecimal("100000.00"), new BigDecimal("5000.00"));

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> clientService.adjustReservedCash(id, buildReservedCashRequest("99999.00")))
                .isInstanceOf(InvalidReservationException.class)
                .extracting("errorCode")
                .isEqualTo("RESERVED_CASH_EXCEEDS_BALANCE");
    }

    @Test
    void shouldThrowClientNotFoundWhenAdjustingReservedCashForUnknownId() {
        UUID id = UUID.randomUUID();
        when(clientRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.adjustReservedCash(id, buildReservedCashRequest("1000.00")))
                .isInstanceOf(ClientNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo("CLIENT_NOT_FOUND");
    }

    @Test
    void shouldReturnExpiredKycForClient005() {
        UUID id = UUID.fromString("a5000000-0000-0000-0000-000000000005");
        Client client = buildClient(id, "Robert Wilson", ClientStatus.ACTIVE, true,
                KycStatus.EXPIRED, LocalDate.of(2025, 1, 1));
        ClientProfileResponse expected = ClientProfileResponse.builder()
                .clientId(id).name("Robert Wilson").accountType(AccountType.CASH)
                .status(ClientStatus.ACTIVE).tradingEnabled(true)
                .kycStatus(KycStatus.EXPIRED).kycExpiryDate(LocalDate.of(2025, 1, 1))
                .build();

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));
        when(clientMapper.toProfileResponse(client)).thenReturn(expected);

        ClientProfileResponse result = clientService.getClient(id);

        assertThat(result.getKycStatus()).isEqualTo(KycStatus.EXPIRED);
    }
}
