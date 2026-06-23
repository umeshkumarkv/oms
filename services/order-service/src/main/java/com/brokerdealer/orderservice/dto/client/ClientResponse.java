package com.brokerdealer.orderservice.dto.client;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class ClientResponse {
    private ClientData data;

    @Data
    public static class ClientData {
        private UUID clientId;
        private String name;
        private String status;
        private Boolean tradingEnabled;
        private String kycStatus;
        private LocalDate kycExpiryDate;
        private Boolean marginCallActive;
        private String cashBalance;
        private Integer maxOpenOrders;
        private List<HoldingData> holdings;
    }

    @Data
    public static class HoldingData {
        private String symbol;
        private Integer quantity;
        private String avgPrice;
    }
}
