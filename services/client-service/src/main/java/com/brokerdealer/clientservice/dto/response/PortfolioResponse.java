package com.brokerdealer.clientservice.dto.response;

import com.brokerdealer.clientservice.domain.ClientStatus;
import com.brokerdealer.clientservice.domain.KycStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class PortfolioResponse {
    UUID clientId;
    String name;
    ClientStatus status;
    Boolean tradingEnabled;
    KycStatus kycStatus;
    LocalDate kycExpiryDate;
    Boolean marginCallActive;
    Integer maxOpenOrders;
    String cashBalance;
    String reservedCash;
    String buyingPower;
    List<HoldingDetailResponse> holdings;
}
