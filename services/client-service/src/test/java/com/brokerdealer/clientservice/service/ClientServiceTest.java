package com.brokerdealer.clientservice.service;

import com.brokerdealer.clientservice.domain.*;
import com.brokerdealer.clientservice.dto.response.ClientProfileResponse;
import com.brokerdealer.clientservice.exception.ClientNotFoundException;
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
