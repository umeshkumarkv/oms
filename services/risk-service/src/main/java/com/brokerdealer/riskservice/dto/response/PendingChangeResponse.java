package com.brokerdealer.riskservice.dto.response;

import com.brokerdealer.riskservice.domain.PendingChangeStatus;
import com.brokerdealer.riskservice.domain.PendingChangeType;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class PendingChangeResponse {

    UUID id;
    String ruleCode;
    PendingChangeType changeType;
    Boolean proposedEnabled;
    String proposedThreshold;
    String makerUserId;
    String makerUsername;
    PendingChangeStatus status;
    String makerNote;
    String rejectionReason;
    String checkerUserId;
    Instant submittedAt;
    Instant reviewedAt;
}
