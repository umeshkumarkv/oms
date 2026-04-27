package com.brokerdealer.clientservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PortfolioResponse {
    String cashBalance;
    String reservedCash;
    String buyingPower;
    List<HoldingDetailResponse> holdings;
}
