package com.brokerdealer.clientservice.dto.response;

import com.brokerdealer.clientservice.domain.AccountType;
import com.brokerdealer.clientservice.domain.ClientStatus;
import com.brokerdealer.clientservice.domain.KycStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.UUID;

@Value
@Builder
public class ClientProfileResponse {
    UUID clientId;
    String name;
    AccountType accountType;
    ClientStatus status;
    Boolean tradingEnabled;
    KycStatus kycStatus;
    LocalDate kycExpiryDate;
}
