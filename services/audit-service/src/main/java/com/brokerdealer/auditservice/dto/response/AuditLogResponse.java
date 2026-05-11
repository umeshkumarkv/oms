package com.brokerdealer.auditservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuditLogResponse {
    String id;
    String entityType;
    String entityId;
    String previousState;
    String newState;
    String actorId;
    String actorType;
    String eventTimestamp;
    String metadata;
}
