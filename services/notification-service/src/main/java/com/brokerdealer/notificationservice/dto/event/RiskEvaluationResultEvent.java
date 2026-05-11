package com.brokerdealer.notificationservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskEvaluationResultEvent {

    private UUID orderId;
    private UUID clientId;
    private UUID userId;
    private String result;
    private String ruleCode;
    private String reason;
    private Instant timestamp;
}
