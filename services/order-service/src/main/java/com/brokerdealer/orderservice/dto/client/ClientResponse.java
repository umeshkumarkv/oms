package com.brokerdealer.orderservice.dto.client;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Envelope DTO for GET /api/v1/clients/{id} response from client-service.
 */
@Data
public class ClientResponse {
    private ClientData data;

    @Data
    public static class ClientData {
        private UUID clientId;
        private String name;
        private String status;        // "ACTIVE" | "BLOCKED"
        private Boolean tradingEnabled;
        private String kycStatus;
        private LocalDate kycExpiryDate;
        private Boolean marginCallActive;
    }
}
