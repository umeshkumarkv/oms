package com.brokerdealer.clientservice.service;

import com.brokerdealer.clientservice.client.MarketDataClient;
import com.brokerdealer.clientservice.domain.*;
import com.brokerdealer.clientservice.dto.request.ReservedCashRequest;
import com.brokerdealer.clientservice.dto.request.UpdateDefaultRouteRequest;
import com.brokerdealer.clientservice.dto.response.ActiveClientResponse;
import com.brokerdealer.clientservice.dto.response.ClientProfileResponse;
import com.brokerdealer.clientservice.dto.response.PortfolioResponse;
import com.brokerdealer.clientservice.dto.response.ReservedCashResponse;
import com.brokerdealer.clientservice.dto.response.UpdateDefaultRouteResponse;
import com.brokerdealer.clientservice.exception.ClientNotFoundException;
import com.brokerdealer.clientservice.exception.InvalidReservationException;
import com.brokerdealer.clientservice.exception.ValidationException;
import com.brokerdealer.clientservice.mapper.ClientMapper;
import com.brokerdealer.clientservice.repository.ClientRepository;
import com.brokerdealer.clientservice.repository.HoldingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    @Mock
    RedisTemplate<String, Object> redisTemplate;

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
                .turnoverToday(BigDecimal.ZERO)
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
                .turnoverToday(BigDecimal.ZERO)
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

    @Test
    void shouldReturnPortfolioWithDefaultRoute() {
        UUID id = UUID.fromString("a1000000-0000-0000-0000-000000000001");
        Client client = buildClientWithBalance(id, new BigDecimal("100000.00"), BigDecimal.ZERO);
        client.setDefaultRoute(DefaultRoute.NYSE);

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));
        when(holdingRepository.findByClientId(id)).thenReturn(Collections.emptyList());

        PortfolioResponse result = clientService.getPortfolio(id);

        assertThat(result.getDefaultRoute()).isEqualTo(DefaultRoute.NYSE);
    }

    private UpdateDefaultRouteRequest buildUpdateDefaultRouteRequest(String defaultRoute) {
        UpdateDefaultRouteRequest req = new UpdateDefaultRouteRequest();
        req.setDefaultRoute(defaultRoute);
        return req;
    }

    @Test
    void shouldUpdateDefaultRouteToNyse() {
        UUID id = UUID.randomUUID();
        Client client = buildClientWithBalance(id, new BigDecimal("100000.00"), BigDecimal.ZERO);
        client.setDefaultRoute(DefaultRoute.NASDAQ);

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenReturn(client);

        UpdateDefaultRouteResponse result = clientService.updateDefaultRoute(id, buildUpdateDefaultRouteRequest("NYSE"));

        assertThat(result.getClientId()).isEqualTo(id);
        assertThat(result.getDefaultRoute()).isEqualTo(DefaultRoute.NYSE);
        verify(clientRepository).save(client);
    }

    @Test
    void shouldUpdateDefaultRouteToAllValidValues() {
        for (DefaultRoute route : DefaultRoute.values()) {
            UUID id = UUID.randomUUID();
            Client client = buildClientWithBalance(id, new BigDecimal("100000.00"), BigDecimal.ZERO);
            client.setDefaultRoute(DefaultRoute.NASDAQ);

            when(clientRepository.findById(id)).thenReturn(Optional.of(client));
            when(clientRepository.save(any(Client.class))).thenReturn(client);

            UpdateDefaultRouteResponse result = clientService.updateDefaultRoute(id, buildUpdateDefaultRouteRequest(route.name()));

            assertThat(result.getDefaultRoute()).isEqualTo(route);
        }
    }

    @Test
    void shouldThrowValidationExceptionForInvalidRouteValue() {
        UUID id = UUID.randomUUID();
        Client client = buildClientWithBalance(id, new BigDecimal("100000.00"), BigDecimal.ZERO);

        when(clientRepository.findById(id)).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> clientService.updateDefaultRoute(id, buildUpdateDefaultRouteRequest("LONDON")))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_ROUTE");
    }

    @Test
    void shouldThrowClientNotFoundWhenUpdatingDefaultRouteForUnknownId() {
        UUID id = UUID.randomUUID();
        when(clientRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.updateDefaultRoute(id, buildUpdateDefaultRouteRequest("NYSE")))
                .isInstanceOf(ClientNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo("CLIENT_NOT_FOUND");
    }

    // --- getActiveClients tests ---

    @Test
    void shouldReturnRankedActiveClientsWhenLimitIsValid() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Client c1 = buildClientWithBalance(id1, new BigDecimal("100000.00"), new BigDecimal("5000.00"));
        c1.setName("Alice Corp");
        c1.setTurnoverToday(new BigDecimal("50000.00"));
        Client c2 = buildClientWithBalance(id2, new BigDecimal("80000.00"), BigDecimal.ZERO);
        c2.setName("Bob Ltd");
        c2.setTurnoverToday(new BigDecimal("20000.00"));

        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("client:active-ranked:today:10")).thenReturn(null);
        when(clientRepository.findTopByTurnoverTodayDesc(any(Pageable.class))).thenReturn(List.of(c1, c2));

        List<ActiveClientResponse> result = clientService.getActiveClients(10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getClientId()).isEqualTo(id1);
        assertThat(result.get(0).getClientName()).isEqualTo("Alice Corp");
        assertThat(result.get(0).getTurnoverToday()).isEqualTo("50000.00");
        assertThat(result.get(0).getAvailableCash()).isEqualTo("95000.00");
        assertThat(result.get(0).getCashLimitWarning()).isFalse();
        assertThat(result.get(1).getClientId()).isEqualTo(id2);
        assertThat(result.get(1).getTurnoverToday()).isEqualTo("20000.00");
        assertThat(result.get(1).getAvailableCash()).isEqualTo("80000.00");
        assertThat(result.get(1).getCashLimitWarning()).isFalse();
    }

    @Test
    void shouldReturnEmptyListWhenNoTurnovers() {
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("client:active-ranked:today:10")).thenReturn(null);
        when(clientRepository.findTopByTurnoverTodayDesc(any(Pageable.class))).thenReturn(Collections.emptyList());

        List<ActiveClientResponse> result = clientService.getActiveClients(10);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowValidationErrorWhenLimitIsZero() {
        assertThatThrownBy(() -> clientService.getActiveClients(0))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void shouldThrowValidationErrorWhenLimitIsNegative() {
        assertThatThrownBy(() -> clientService.getActiveClients(-1))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void shouldThrowValidationErrorWhenLimitExceedsFifty() {
        assertThatThrownBy(() -> clientService.getActiveClients(51))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void shouldReturnCachedResultWhenCacheHit() {
        UUID id = UUID.randomUUID();
        List<Map<String, String>> cached = List.of(Map.of(
                "clientId", id.toString(),
                "clientName", "Cached Corp",
                "turnoverToday", "75000.00",
                "availableCash", "25000.00",
                "cashLimitWarning", "false"));

        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("client:active-ranked:today:5")).thenReturn(cached);

        List<ActiveClientResponse> result = clientService.getActiveClients(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClientId()).isEqualTo(id);
        assertThat(result.get(0).getClientName()).isEqualTo("Cached Corp");
        assertThat(result.get(0).getTurnoverToday()).isEqualTo("75000.00");
        assertThat(result.get(0).getAvailableCash()).isEqualTo("25000.00");
        assertThat(result.get(0).getCashLimitWarning()).isFalse();
        verify(clientRepository, never()).findTopByTurnoverTodayDesc(any(Pageable.class));
    }

    @Test
    void shouldDegradeGracefullyWhenRedisReadFails() {
        UUID id = UUID.randomUUID();
        Client c = buildClientWithBalance(id, new BigDecimal("50000.00"), BigDecimal.ZERO);
        c.setName("Fallback Corp");
        c.setTurnoverToday(new BigDecimal("10000.00"));

        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis connection failed"));
        when(clientRepository.findTopByTurnoverTodayDesc(any(Pageable.class))).thenReturn(List.of(c));

        List<ActiveClientResponse> result = clientService.getActiveClients(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClientName()).isEqualTo("Fallback Corp");
    }

    @Test
    void shouldDegradeGracefullyWhenRedisWriteFails() {
        UUID id = UUID.randomUUID();
        Client c = buildClientWithBalance(id, new BigDecimal("50000.00"), BigDecimal.ZERO);
        c.setName("WriteFailCorp");
        c.setTurnoverToday(new BigDecimal("5000.00"));

        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        doThrow(new RuntimeException("Redis write failed"))
                .when(valueOps).set(anyString(), any(), anyLong(), any(TimeUnit.class));
        when(clientRepository.findTopByTurnoverTodayDesc(any(Pageable.class))).thenReturn(List.of(c));

        List<ActiveClientResponse> result = clientService.getActiveClients(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClientName()).isEqualTo("WriteFailCorp");
    }

    @Test
    void shouldComputeCashLimitWarningTrueWhenAvailableCashBelowThreshold() {
        UUID id = UUID.randomUUID();
        // dailyOrderLimit = 100000, threshold = 10000; availableCash = 5000 → warning
        Client c = buildClientWithBalance(id, new BigDecimal("5000.00"), BigDecimal.ZERO);
        c.setName("Warning Corp");
        c.setTurnoverToday(new BigDecimal("90000.00"));
        c.setDailyOrderLimit(new BigDecimal("100000.00"));

        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(clientRepository.findTopByTurnoverTodayDesc(any(Pageable.class))).thenReturn(List.of(c));

        List<ActiveClientResponse> result = clientService.getActiveClients(10);

        assertThat(result.get(0).getCashLimitWarning()).isTrue();
    }

    @Test
    void shouldComputeCashLimitWarningFalseWhenAvailableCashAboveThreshold() {
        UUID id = UUID.randomUUID();
        // dailyOrderLimit = 100000, threshold = 10000; availableCash = 50000 → no warning
        Client c = buildClientWithBalance(id, new BigDecimal("50000.00"), BigDecimal.ZERO);
        c.setName("Safe Corp");
        c.setTurnoverToday(new BigDecimal("10000.00"));
        c.setDailyOrderLimit(new BigDecimal("100000.00"));

        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(clientRepository.findTopByTurnoverTodayDesc(any(Pageable.class))).thenReturn(List.of(c));

        List<ActiveClientResponse> result = clientService.getActiveClients(10);

        assertThat(result.get(0).getCashLimitWarning()).isFalse();
    }

    @Test
    void shouldComputeCashLimitWarningFalseWhenDailyOrderLimitIsNull() {
        UUID id = UUID.randomUUID();
        // dailyOrderLimit is null → warning must always be false
        Client c = buildClientWithBalance(id, new BigDecimal("100.00"), BigDecimal.ZERO);
        c.setName("No Limit Corp");
        c.setTurnoverToday(new BigDecimal("500.00"));
        // dailyOrderLimit not set — null

        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(clientRepository.findTopByTurnoverTodayDesc(any(Pageable.class))).thenReturn(List.of(c));

        List<ActiveClientResponse> result = clientService.getActiveClients(10);

        assertThat(result.get(0).getCashLimitWarning()).isFalse();
    }

    // --- processExecutionFill turnoverToday tests ---

    @Test
    void shouldIncrementTurnoverOnBuyFillInProcessExecutionFill() {
        UUID clientId = UUID.randomUUID();
        Client client = buildClientWithBalance(clientId, new BigDecimal("100000.00"), new BigDecimal("10000.00"));
        client.setTurnoverToday(new BigDecimal("20000.00"));

        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenReturn(client);
        when(holdingRepository.findByClientIdAndSymbol(eq(clientId), anyString())).thenReturn(Optional.empty());

        Map<String, Object> payload = new HashMap<>();
        payload.put("clientId", clientId.toString());
        payload.put("side", "BUY");
        payload.put("fillType", "FULL_FILL");
        payload.put("executedQty", 100);
        payload.put("executedPrice", "150.00");
        payload.put("fillValue", "15000.00");
        payload.put("symbol", "AAPL");

        clientService.processExecutionFill(payload);

        assertThat(client.getTurnoverToday()).isEqualByComparingTo(new BigDecimal("35000.00"));
    }

    @Test
    void shouldIncrementTurnoverOnSellFillInProcessExecutionFill() {
        UUID clientId = UUID.randomUUID();
        Client client = buildClientWithBalance(clientId, new BigDecimal("50000.00"), BigDecimal.ZERO);
        client.setTurnoverToday(new BigDecimal("10000.00"));

        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenReturn(client);
        Holding holding = buildHolding(clientId, "TSLA", 200, "200.0000");
        when(holdingRepository.findByClientIdAndSymbol(eq(clientId), anyString())).thenReturn(Optional.of(holding));

        Map<String, Object> payload = new HashMap<>();
        payload.put("clientId", clientId.toString());
        payload.put("side", "SELL");
        payload.put("fillType", "FULL_FILL");
        payload.put("executedQty", 50);
        payload.put("executedPrice", "210.00");
        payload.put("fillValue", "10500.00");
        payload.put("symbol", "TSLA");

        clientService.processExecutionFill(payload);

        assertThat(client.getTurnoverToday()).isEqualByComparingTo(new BigDecimal("20500.00"));
    }

    @Test
    void shouldNotIncrementTurnoverOnRejectedFillInProcessExecutionFill() {
        UUID clientId = UUID.randomUUID();
        Client client = buildClientWithBalance(clientId, new BigDecimal("100000.00"), new BigDecimal("5000.00"));
        client.setTurnoverToday(new BigDecimal("0.00"));

        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientRepository.save(any(Client.class))).thenReturn(client);

        Map<String, Object> payload = new HashMap<>();
        payload.put("clientId", clientId.toString());
        payload.put("side", "BUY");
        payload.put("fillType", "REJECTED");
        payload.put("executedQty", 100);
        payload.put("executedPrice", "150.00");
        payload.put("fillValue", "15000.00");
        payload.put("symbol", "AAPL");
        payload.put("remainingQuantity", 100);
        payload.put("marketPriceAtFill", "150.00");

        clientService.processExecutionFill(payload);

        assertThat(client.getTurnoverToday()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
