package com.brokerdealer.auditservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AuditTrailResponse {
    AuditTrailData data;
    ResponseMeta meta;

    @Value
    @Builder
    public static class AuditTrailData {
        List<AuditLogResponse> records;
        long totalElements;
    }
}
