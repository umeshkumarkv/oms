package com.brokerdealer.orderservice.dto.market;

import lombok.Data;

/**
 * Envelope DTO for GET /api/v1/market/hours response from marketdata-service.
 */
@Data
public class MarketHoursResponse {
    private MarketHoursData data;

    @Data
    public static class MarketHoursData {
        private Boolean marketOpen;
        private String openTime;
        private String closeTime;
        private String timezone;
    }
}
