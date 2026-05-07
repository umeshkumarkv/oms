package com.brokerdealer.executionservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketDataResponse {
    private String symbol;
    private BigDecimal lastPrice;
    private Boolean tradable;
    private Boolean halted;
}
