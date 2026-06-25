package com.brokerdealer.orderservice.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskEvaluationResultEvent {

    private UUID orderId;
    private String result;    // "PASSED" | "REJECTED"
    private String violatedRuleCode;  // null if PASSED
    private String rejectionReason;   // null if PASSED
}
