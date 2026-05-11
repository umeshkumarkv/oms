package com.brokerdealer.orderservice.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStateChangedEvent {

    private UUID orderId;
    private UUID clientId;
    private UUID userId;         // auth-service userId (createdBy) — for targeted WS push
    private String fromState;    // null on initial NEW publication
    private String toState;
    private String triggeredBy;  // "USER:{userId}" | "SYSTEM:OMS" | "SYSTEM:RISK_ENGINE"
    private String timestamp;    // ISO 8601 UTC
    private Map<String, String> metadata;
}
